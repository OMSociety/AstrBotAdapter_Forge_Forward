package io.github.railgun19457.astrbotadapter.platform.forge.listener;

import io.github.railgun19457.astrbotadapter.platform.forge.ForgePlayer;
import io.github.railgun19457.astrbotadapter.service.notification.NotificationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 玩家加入/离开监听器
 */
public class ForgePlayerListener {

    private final NotificationService notificationService;

    public ForgePlayerListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 记录加入时间（独立于通知开关，供在线时长统计使用）
        ForgePlayer.markJoin(player.getUUID());
        if (notificationService == null) {
            return;
        }
        String playerName = player.getGameProfile().getName();
        String displayName = player.getDisplayName() != null
                ? player.getDisplayName().getString() : playerName;
        notificationService.notifyPlayerJoin(player.getUUID(), playerName, displayName);
    }

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ForgePlayer.markQuit(player.getUUID());
        if (notificationService == null) {
            return;
        }
        String playerName = player.getGameProfile().getName();
        String displayName = player.getDisplayName() != null
                ? player.getDisplayName().getString() : playerName;
        notificationService.notifyPlayerQuit(player.getUUID(), playerName, displayName, null);
    }
}
