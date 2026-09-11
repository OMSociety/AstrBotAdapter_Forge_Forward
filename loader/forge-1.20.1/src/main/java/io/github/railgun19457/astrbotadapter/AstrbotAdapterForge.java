package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.platform.forge.ForgePlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.forge.command.AstrbotCommand;
import io.github.railgun19457.astrbotadapter.platform.forge.listener.ForgeChatListener;
import io.github.railgun19457.astrbotadapter.platform.forge.listener.ForgePlayerListener;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Astrbot Adapter - Forge 1.20.1 服务端模组入口
 * 服务器启动时初始化统一服务器（REST + WebSocket），关闭时优雅停机。
 */
@Mod(AstrbotAdapterForge.MOD_ID)
public class AstrbotAdapterForge {

    public static final String MOD_ID = "astrbotadapter";

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("AstrbotAdapter");

    private ForgeAdapterPlugin plugin;

    public AstrbotAdapterForge() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Astrbot Adapter (Forge) 已加载，等待服务器启动...");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        plugin = new ForgeAdapterPlugin(server);
        plugin.initialize();

        // 注意：Forge 中 RegisterCommandsEvent 在 MinecraftServer 构造函数（创建 Commands 时）
        // 触发，早于 ServerStartingEvent；此时 plugin 尚未创建，因此命令需在此处补注册。
        // （Brigadier dispatcher 在构造函数中已就绪，随时可注册命令）
        AstrbotCommand.register(server.getCommands().getDispatcher(), plugin);
        LOGGER.info("游戏内指令 /astrbot 已注册");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (plugin != null) {
            plugin.shutdown();
            plugin = null;
        }
    }

    /**
     * Forge 平台插件包装器
     */
    private static class ForgeAdapterPlugin extends AstrbotAdapterPlugin {

        private final MinecraftServer server;
        private ForgeChatListener chatListener;
        private ForgePlayerListener playerListener;

        public ForgeAdapterPlugin(MinecraftServer server) {
            this.server = server;
            Path configDir = FMLPaths.CONFIGDIR.get();
            this.dataFolder = configDir.resolve("astrbotadapter").toFile();
            this.logger = createBridgeLogger();
        }

        @Override
        protected void initializePlatform() {
            this.platformAdapter = new ForgePlatformAdapter(server, logger);
            platformAdapter.initialize();
            logger.info("Forge 平台适配器已初始化");
        }

        @Override
        protected void initializeBeforeStart() {
            registerForgeListeners();
        }

        @Override
        protected void shutdown() {
            unregisterForgeListeners();
            super.shutdown();
        }

        private void registerForgeListeners() {
            if (chatService != null || messageForwardService != null) {
                chatListener = new ForgeChatListener(chatService, messageForwardService);
                MinecraftForge.EVENT_BUS.register(chatListener);
                logger.info("Forge 聊天监听器已注册");
            }
            // 玩家监听器始终注册：即便通知功能关闭，也需维护在线时长统计
            playerListener = new ForgePlayerListener(notificationService);
            MinecraftForge.EVENT_BUS.register(playerListener);
            logger.info("Forge 玩家监听器已注册");
        }

        private void unregisterForgeListeners() {
            if (chatListener != null) {
                MinecraftForge.EVENT_BUS.unregister(chatListener);
                chatListener = null;
            }
            if (playerListener != null) {
                MinecraftForge.EVENT_BUS.unregister(playerListener);
                playerListener = null;
            }
        }

        /**
         * 将 java.util.logging 日志桥接到 SLF4J（Forge 日志体系）
         */
        private static java.util.logging.Logger createBridgeLogger() {
            java.util.logging.Logger jul = java.util.logging.Logger.getLogger("AstrbotAdapter");
            jul.setUseParentHandlers(false);
            jul.setLevel(Level.ALL);
            jul.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    String msg = record.getMessage();
                    if (msg == null) {
                        return;
                    }
                    if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                        LOGGER.error(msg);
                    } else if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                        LOGGER.warn(msg);
                    } else if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                        LOGGER.info(msg);
                    } else {
                        LOGGER.debug(msg);
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
            return jul;
        }
    }
}
