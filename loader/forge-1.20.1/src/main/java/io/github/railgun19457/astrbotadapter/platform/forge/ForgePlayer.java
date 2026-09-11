package io.github.railgun19457.astrbotadapter.platform.forge;

import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Forge 平台玩家实现
 */
public class ForgePlayer implements CommonPlayer {

    /** 玩家 UUID → 本次加入服务器时间戳（毫秒），由 ForgePlayerListener 维护 */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> JOIN_TIMES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final ServerPlayer player;

    public ForgePlayer(ServerPlayer player) {
        this.player = player;
    }

    /**
     * 玩家登录时记录加入时间（由 ForgePlayerListener 调用）
     */
    public static void markJoin(UUID uuid) {
        JOIN_TIMES.put(uuid, System.currentTimeMillis());
    }

    /**
     * 玩家退出时移除记录（由 ForgePlayerListener 调用）
     */
    public static void markQuit(UUID uuid) {
        JOIN_TIMES.remove(uuid);
    }

    /**
     * 获取底层 Forge 玩家对象
     */
    public ServerPlayer getForgePlayer() {
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
        // 1.20.1 中 ping 延迟存放在 ServerPlayer.latency（SRG: f_8943_），public 字段直接读
        // 注意：ServerGamePacketListenerImpl.connection 是 private，且 1.20.1 尚无 latency 字段
        // （1.20.2+ 才拆出 ServerCommonPacketListenerImpl），勿改回 player.connection.latency
        return player.latency;
    }

    @Override
    public void sendMessage(String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public boolean hasPermission(String permission) {
        // Forge 无权限插件时按 OP 权限等级处理
        return player.hasPermissions(2);
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
        return player.server.getPlayerList().isOp(player.getGameProfile());
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
