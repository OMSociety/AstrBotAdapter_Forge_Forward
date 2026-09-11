package io.github.railgun19457.astrbotadapter.platform.forge.listener;

import io.github.railgun19457.astrbotadapter.core.util.PlaceholderUtil;
import io.github.railgun19457.astrbotadapter.service.chat.ChatService;
import io.github.railgun19457.astrbotadapter.service.forward.MessageForwardService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 聊天监听器
 * 转发玩家聊天到 AI 聊天服务 / 消息转发服务。
 */
public class ForgeChatListener {

    private final ChatService chatService;
    private final MessageForwardService forwardService;

    public ForgeChatListener(ChatService chatService, MessageForwardService forwardService) {
        this.chatService = chatService;
        this.forwardService = forwardService;
    }

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getRawText();
        String playerName = player.getGameProfile().getName();
        String displayName = player.getDisplayName() != null
                ? player.getDisplayName().getString() : playerName;

        // 先检查 AI 聊天
        if (chatService != null && chatService.shouldTriggerChat(message)) {
            boolean isPrivate = chatService.isPrivateChatTrigger(message);

            boolean handled = chatService.handlePlayerChat(
                    player.getUUID(), playerName, displayName, message);
            if (handled) {
                // 私聊模式：取消消息发送，模拟显示给玩家自己
                if (isPrivate) {
                    event.setCanceled(true);
                    String echoFormat = chatService.getConfig().getPrivateChatEchoFormat();
                    String echoMessage = PlaceholderUtil.replace(echoFormat,
                            "player", displayName,
                            "message", message);
                    player.sendSystemMessage(Component.literal(echoMessage));
                }
                // 群聊模式：不取消，让其他玩家也能看到原始消息
                return;
            }
        }

        // 检查消息转发
        if (forwardService != null && forwardService.shouldForward(message)) {
            forwardService.handlePlayerMessage(
                    player.getUUID(), playerName, displayName, message);
        }
    }
}
