package io.github.railgun19457.astrbotadapter.platform.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.railgun19457.astrbotadapter.AstrbotAdapterPlugin;
import io.github.railgun19457.astrbotadapter.communication.UnifiedServer;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * /astrbot 管理命令（brigadier 实现）
 *
 * <p>子命令：help / status / reload / token show|regen / connections。
 * Forge 无权限系统，敏感子命令（reload / token / connections）要求 op 等级 2。</p>
 */
public final class AstrbotCommand {

    private AstrbotCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                AstrbotAdapterPlugin plugin) {
        dispatcher.register(
                Commands.literal("astrbot")
                        .executes(ctx -> sendHelp(ctx, plugin))
                        .then(Commands.literal("help")
                                .executes(ctx -> sendHelp(ctx, plugin)))
                        .then(Commands.literal("status")
                                .executes(ctx -> sendStatus(ctx, plugin)))
                        .then(Commands.literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> doReload(ctx, plugin)))
                        .then(Commands.literal("token")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("show")
                                        .executes(ctx -> showToken(ctx, plugin)))
                                .then(Commands.literal("regen")
                                        .executes(ctx -> regenToken(ctx, plugin))))
                        .then(Commands.literal("connections")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> showConnections(ctx, plugin)))
        );
    }

    // ---- 子命令实现 ----

    private static int sendHelp(CommandContext<CommandSourceStack> ctx, AstrbotAdapterPlugin plugin) {
        CommandSourceStack src = ctx.getSource();
        send(src, "&e/astrbot help &7- 显示帮助信息");
        send(src, "&e/astrbot status &7- 查看插件状态");
        send(src, "&e/astrbot reload &7- 重载配置 &8(op 2)");
        send(src, "&e/astrbot token <show|regen> &7- 管理认证令牌 &8(op 2)");
        send(src, "&e/astrbot connections &7- 查看 WebSocket 连接数 &8(op 2)");
        return 1;
    }

    private static int sendStatus(CommandContext<CommandSourceStack> ctx, AstrbotAdapterPlugin plugin) {
        CommandSourceStack src = ctx.getSource();
        PluginConfig config = plugin.getConfigManager().getConfig();
        UnifiedServer server = plugin.getUnifiedServer();
        PlatformAdapter adapter = plugin.getPlatformAdapter();

        boolean running = server != null && server.isRunning();
        send(src, "&6[AstrbotAdapter] 状态");
        if (adapter != null) {
            send(src, "&7平台: &f" + adapter.getPlatformType().name()
                    + " &8(&f" + adapter.getServerVersion() + "&8)");
        }
        if (config.isProxyModeEnabled()) {
            send(src, "&7模式: &f代理模式（未启动本地服务器）");
        } else {
            send(src, "&7服务器: " + (running ? "&a运行中" : "&c已停止")
                    + " &7(地址: &f" + config.getServerHost() + ":" + config.getServerPort() + "&7)");
            if (server != null) {
                send(src, "&7WebSocket 连接: &f" + server.getConnectionCount());
            }
        }
        return 1;
    }

    private static int doReload(CommandContext<CommandSourceStack> ctx, AstrbotAdapterPlugin plugin) {
        plugin.reload();
        send(ctx.getSource(), "&a配置已重载");
        return 1;
    }

    private static int showToken(CommandContext<CommandSourceStack> ctx, AstrbotAdapterPlugin plugin) {
        String token = plugin.getConfigManager().getConfig().getToken();
        if (token == null || token.isEmpty()) {
            send(ctx.getSource(), "&c当前未设置认证令牌");
        } else {
            send(ctx.getSource(), "&6当前令牌: &f" + token);
        }
        return 1;
    }

    private static int regenToken(CommandContext<CommandSourceStack> ctx, AstrbotAdapterPlugin plugin) {
        String token = plugin.getAuthManager().regenerateToken();
        send(ctx.getSource(), "&a新令牌已生成: &f" + token);
        send(ctx.getSource(), "&7请同步更新 AstrBot 侧配置，旧令牌立即失效");
        return 1;
    }

    private static int showConnections(CommandContext<CommandSourceStack> ctx, AstrbotAdapterPlugin plugin) {
        UnifiedServer server = plugin.getUnifiedServer();
        if (server == null || !server.isRunning()) {
            send(ctx.getSource(), "&c服务器未运行");
            return 1;
        }
        send(ctx.getSource(), "&7WebSocket 连接数: &f" + server.getConnectionCount());
        return 1;
    }

    // ---- 工具 ----

    private static void send(CommandSourceStack src, String message) {
        src.sendSuccess(() -> colorize(message), false);
    }

    /**
     * 将 Bukkit 风格颜色代码（&a 等）解析为带样式的 Component。
     */
    private static Component colorize(String message) {
        ChatFormatting current = null;
        MutableComponent result = Component.empty();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '&' && i + 1 < message.length()) {
                ChatFormatting fmt = fromColorCode(message.charAt(++i));
                if (fmt != null) {
                    if (sb.length() > 0) {
                        result = appendSegment(result, sb.toString(), current);
                        sb.setLength(0);
                    }
                    current = fmt;
                    continue;
                }
            }
            sb.append(c);
        }
        if (sb.length() > 0) {
            result = appendSegment(result, sb.toString(), current);
        }
        return result;
    }

    private static MutableComponent appendSegment(MutableComponent base, String text, ChatFormatting fmt) {
        MutableComponent segment = Component.literal(text);
        if (fmt != null) {
            segment = segment.withStyle(fmt);
        }
        return base.append(segment);
    }

    private static ChatFormatting fromColorCode(char code) {
        return switch (code) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            default -> null;
        };
    }
}
