package io.github.railgun19457.astrbotadapter.platform.neoforge.listener;

import io.github.railgun19457.astrbotadapter.platform.neoforge.NeoForgePlayer;
import io.github.railgun19457.astrbotadapter.service.notification.NotificationService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * NeoForge 玩家加入/离开监听器
 */
public class NeoForgePlayerListener {

    private final NotificationService notificationService;

    public NeoForgePlayerListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 记录加入时间（独立于通知开关，供在线时长统计使用）
        NeoForgePlayer.markJoin(player.getUUID());
        if (notificationService == null) {
            return;
        }
        String playerName = player.getGameProfile().name();
        String displayName = player.getDisplayName() != null
                ? player.getDisplayName().getString() : playerName;
        notificationService.notifyPlayerJoin(player.getUUID(), playerName, displayName);
    }

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        NeoForgePlayer.markQuit(player.getUUID());
        if (notificationService == null) {
            return;
        }
        String playerName = player.getGameProfile().name();
        String displayName = player.getDisplayName() != null
                ? player.getDisplayName().getString() : playerName;
        notificationService.notifyPlayerQuit(player.getUUID(), playerName, displayName, null);
    }
}
