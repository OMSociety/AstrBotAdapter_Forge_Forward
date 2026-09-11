package io.github.railgun19457.astrbotadapter.platform.forge;

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
 * Forge 平台适配器
 * 对接 MinecraftServer / ServerPlayer，实现 PlatformAdapter 接口。
 */
public class ForgePlatformAdapter implements PlatformAdapter {

    private final MinecraftServer server;
    private final java.util.logging.Logger logger;
    private final CommonServer serverInfo;
    private final CommonScheduler scheduler;
    private final ForgeTpsTracker tpsTracker;
    private final long startTime;

    public ForgePlatformAdapter(MinecraftServer server, java.util.logging.Logger logger) {
        this.server = server;
        this.logger = logger;
        this.tpsTracker = new ForgeTpsTracker();
        this.serverInfo = new ForgeServer(server, tpsTracker);
        this.scheduler = new ForgeScheduler(server);
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.FORGE;
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
        return "Forge";
    }

    @Override
    public CommonServer getServer() {
        return serverInfo;
    }

    @Override
    public Collection<CommonPlayer> getOnlinePlayers() {
        return server.getPlayerList().getPlayers().stream()
                .map(ForgePlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CommonPlayer> getPlayer(String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        return player != null ? Optional.of(new ForgePlayer(player)) : Optional.empty();
    }

    @Override
    public Optional<CommonPlayer> getPlayer(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player != null ? Optional.of(new ForgePlayer(player)) : Optional.empty();
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
        if (player instanceof ForgePlayer) {
            ((ForgePlayer) player).getForgePlayer()
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
        if (!(player instanceof ForgePlayer)) {
            return false;
        }
        ServerPlayer serverPlayer = ((ForgePlayer) player).getForgePlayer();

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
            return server.getCommands()
                    .performPrefixedCommand(server.createCommandSourceStack(), command) >= 0;
        } catch (Exception e) {
            logger.warning("执行指令失败 [" + command + "]: " + e.getMessage());
            return false;
        }
    }

    private boolean runCommandAs(ServerPlayer player, String command) {
        try {
            return server.getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack(), command) >= 0;
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
        logger.info("Forge 适配器已初始化（TPS 统计已启动）");
    }

    @Override
    public void shutdown() {
        tpsTracker.unregister();
        scheduler.cancelAll();
        logger.info("Forge 适配器已关闭");
    }

    @Override
    public void registerListeners() {
        // 监听器由插件主类注册到 MinecraftForge.EVENT_BUS
    }

    @Override
    public void unregisterListeners() {
        // 监听器由插件主类注销
    }
}
