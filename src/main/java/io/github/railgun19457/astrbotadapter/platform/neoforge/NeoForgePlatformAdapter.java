package io.github.railgun19457.astrbotadapter.platform.neoforge;

import io.github.railgun19457.astrbotadapter.core.util.LogReader;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.PlatformType;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * NeoForge 平台适配器
 * 对接 MinecraftServer / ServerPlayer，实现 PlatformAdapter 接口。
 */
public class NeoForgePlatformAdapter implements PlatformAdapter {

    private final MinecraftServer server;
    private final java.util.logging.Logger logger;
    private final CommonServer serverInfo;
    private final CommonScheduler scheduler;
    private final NeoForgeTpsTracker tpsTracker;
    private final long startTime;

    public NeoForgePlatformAdapter(MinecraftServer server, java.util.logging.Logger logger) {
        this.server = server;
        this.logger = logger;
        this.tpsTracker = new NeoForgeTpsTracker();
        this.serverInfo = new NeoForgeServer(server, tpsTracker);
        this.scheduler = new NeoForgeScheduler(server);
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public PlatformType getPlatformType() {
        // common/ 的 PlatformType 枚举目前没有 NEOFORGE 常量（该目录本次不修改），
        // 故此处回落到 UNKNOWN；对外展示名以 getServerName() 的 "NeoForge" 为准。
        return PlatformType.UNKNOWN;
    }

    @Override
    public String getServerVersion() {
        return server.getServerVersion();
    }

    @Override
    public String getServerMotd() {
        return server.getMotd();
    }

    @Override
    public long getServerUptime() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public String getServerName() {
        return "NeoForge";
    }

    @Override
    public CommonServer getServer() {
        return serverInfo;
    }

    @Override
    public Collection<CommonPlayer> getOnlinePlayers() {
        return server.getPlayerList().getPlayers().stream()
                .map(NeoForgePlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CommonPlayer> getPlayer(String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        return player != null ? Optional.of(new NeoForgePlayer(player)) : Optional.empty();
    }

    @Override
    public Optional<CommonPlayer> getPlayer(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player != null ? Optional.of(new NeoForgePlayer(player)) : Optional.empty();
    }

    @Override
    public int getOnlinePlayerCount() {
        return server.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return server.getMaxPlayers();
    }

    @Override
    public void broadcastMessage(String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    @Override
    public void sendMessage(CommonPlayer player, String message) {
        if (player instanceof NeoForgePlayer) {
            ((NeoForgePlayer) player).getServerPlayer()
                    .sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public void sendConsoleMessage(String message) {
        server.sendSystemMessage(Component.literal(message));
    }

    @Override
    public boolean executeCommand(String command) {
        if (scheduler.isMainThread()) {
            return runCommand(command);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        scheduler.runSync(() -> {
            try {
                future.complete(runCommand(command));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("同步执行指令超时或失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean executeCommand(CommonPlayer player, String command) {
        if (!(player instanceof NeoForgePlayer)) {
            return false;
        }
        ServerPlayer serverPlayer = ((NeoForgePlayer) player).getServerPlayer();

        if (scheduler.isMainThread()) {
            return runCommandAs(serverPlayer, command);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        scheduler.runSync(() -> {
            try {
                future.complete(runCommandAs(serverPlayer, command));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("同步执行玩家指令超时或失败: " + e.getMessage());
            return false;
        }
    }

    private boolean runCommand(String command) {
        try {
            // 26.2 起 performPrefixedCommand 返回 void，不再给出结果值，异常即失败
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            return true;
        } catch (Exception e) {
            logger.warning("执行指令失败 [" + command + "]: " + e.getMessage());
            return false;
        }
    }

    private boolean runCommandAs(ServerPlayer player, String command) {
        try {
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
            return true;
        } catch (Exception e) {
            logger.warning("执行玩家指令失败 [" + command + "]: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CommonScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public List<String> getRecentLogs(int lines) {
        return LogReader.getRecentLogs(lines);
    }

    @Override
    public List<String> getLogsByTimeRange(long startTime, long endTime) {
        return LogReader.getLogsByTimeRange(startTime, endTime);
    }

    @Override
    public void initialize() {
        tpsTracker.register();
        logger.info("NeoForge 适配器已初始化（TPS 统计已启动）");
    }

    @Override
    public void shutdown() {
        tpsTracker.unregister();
        scheduler.cancelAll();
        logger.info("NeoForge 适配器已关闭");
    }

    @Override
    public void registerListeners() {
        // 监听器由插件主类注册到 NeoForge.EVENT_BUS
    }

    @Override
    public void unregisterListeners() {
        // 监听器由插件主类注销
    }
}
