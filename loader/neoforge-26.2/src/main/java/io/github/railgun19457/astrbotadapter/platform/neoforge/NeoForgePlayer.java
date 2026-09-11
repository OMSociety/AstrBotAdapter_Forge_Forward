package io.github.railgun19457.astrbotadapter.platform.neoforge;

import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.UUID;

/**
 * NeoForge 平台玩家实现
 */
public class NeoForgePlayer implements CommonPlayer {

    /** 玩家 UUID → 本次加入服务器时间戳（毫秒），由 NeoForgePlayerListener 维护 */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> JOIN_TIMES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final ServerPlayer player;

    public NeoForgePlayer(ServerPlayer player) {
        this.player = player;
    }

    /**
     * 玩家登录时记录加入时间（由 NeoForgePlayerListener 调用）
     */
    public static void markJoin(UUID uuid) {
        JOIN_TIMES.put(uuid, System.currentTimeMillis());
    }

    /**
     * 玩家退出时移除记录（由 NeoForgePlayerListener 调用）
     */
    public static void markQuit(UUID uuid) {
        JOIN_TIMES.remove(uuid);
    }

    /**
     * 获取底层 Minecraft 玩家对象
     */
    public ServerPlayer getServerPlayer() {
        return player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUUID();
    }

    @Override
    public String getName() {
        return player.getGameProfile().getName();
    }

    @Override
    public String getDisplayName() {
        Component displayName = player.getDisplayName();
        return displayName != null ? displayName.getString() : getName();
    }

    @Override
    public int getPing() {
        // 1.20.2 起 ping 延迟拆到 ServerCommonPacketListenerImpl.latency，ServerPlayer 上不再有
        // public latency 字段，需经 connection 访问
        return player.connection.latency;
    }

    @Override
    public void sendMessage(String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public boolean hasPermission(String permission) {
        // 26.2 起 Player.hasPermissions(int) 被新的 net.minecraft.server.permissions 体系取代，
        // COMMANDS_GAMEMASTER 对应旧 OP 等级 2
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public boolean isOnline() {
        return !player.isRemoved();
    }

    @Override
    public double getHealth() {
        return player.getHealth();
    }

    @Override
    public double getMaxHealth() {
        return player.getMaxHealth();
    }

    @Override
    public int getLevel() {
        return player.experienceLevel;
    }

    @Override
    public String getWorld() {
        return player.level().dimension().location().toString();
    }

    @Override
    public PlayerLocation getLocation() {
        return new PlayerLocation(
                getWorld(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    @Override
    public String getGameMode() {
        return player.gameMode.getGameModeForPlayer().getName();
    }

    @Override
    public int getFoodLevel() {
        return player.getFoodData().getFoodLevel();
    }

    @Override
    public float getExp() {
        return player.experienceProgress;
    }

    @Override
    public int getTotalExp() {
        return player.totalExperience;
    }

    @Override
    public boolean isOp() {
        // 26.2 起 PlayerList.isOp 接收 NameAndId（不再是 GameProfile）
        return player.server.getPlayerList().isOp(player.nameAndId());
    }

    @Override
    public boolean isFlying() {
        return player.getAbilities().flying;
    }

    @Override
    public long getOnlineTime() {
        Long join = JOIN_TIMES.get(player.getUUID());
        if (join == null) {
            return -1;
        }
        return Math.max(0, System.currentTimeMillis() - join);
    }
}
