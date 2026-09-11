package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.platform.neoforge.NeoForgePlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.neoforge.command.AstrbotCommand;
import io.github.railgun19457.astrbotadapter.platform.neoforge.listener.NeoForgeChatListener;
import io.github.railgun19457.astrbotadapter.platform.neoforge.listener.NeoForgePlayerListener;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Astrbot Adapter - NeoForge 26.2 服务端模组入口
 * 服务器启动时初始化统一服务器（REST + WebSocket），关闭时优雅停机。
 */
@Mod(AstrbotAdapterNeoForge.MOD_ID)
public class AstrbotAdapterNeoForge {

    public static final String MOD_ID = "astrbotadapter";

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("AstrbotAdapter");

    private NeoForgeAdapterPlugin plugin;

    public AstrbotAdapterNeoForge() {
        // 服务器生命周期 / 玩家 / tick 事件都在 game bus（NeoForge.EVENT_BUS）上派发
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Astrbot Adapter (NeoForge) 已加载，等待服务器启动...");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        plugin = new NeoForgeAdapterPlugin(server);
        plugin.initialize();

        // 注意：NeoForge 中 RegisterCommandsEvent 早于本事件触发（服务器构造 Commands 时），
        // 那时 plugin 还不存在，因此命令在此处补注册。
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
     * NeoForge 平台插件包装器
     */
    private static class NeoForgeAdapterPlugin extends AstrbotAdapterPlugin {

        private final MinecraftServer server;
        private NeoForgeChatListener chatListener;
        private NeoForgePlayerListener playerListener;

        public NeoForgeAdapterPlugin(MinecraftServer server) {
            this.server = server;
            Path configDir = FMLPaths.CONFIGDIR.get();
            this.dataFolder = configDir.resolve("astrbotadapter").toFile();
            this.logger = createBridgeLogger();
        }

        @Override
        protected void initializePlatform() {
            this.platformAdapter = new NeoForgePlatformAdapter(server, logger);
            platformAdapter.initialize();
            logger.info("NeoForge 平台适配器已初始化");
        }

        @Override
        protected void initializeBeforeStart() {
            registerNeoForgeListeners();
        }

        @Override
        protected void shutdown() {
            unregisterNeoForgeListeners();
            super.shutdown();
        }

        private void registerNeoForgeListeners() {
            if (chatService != null || messageForwardService != null) {
                chatListener = new NeoForgeChatListener(chatService, messageForwardService);
                NeoForge.EVENT_BUS.register(chatListener);
                logger.info("NeoForge 聊天监听器已注册");
            }
            // 玩家监听器始终注册：即便通知功能关闭，也需维护在线时长统计
            playerListener = new NeoForgePlayerListener(notificationService);
            NeoForge.EVENT_BUS.register(playerListener);
            logger.info("NeoForge 玩家监听器已注册");
        }

        private void unregisterNeoForgeListeners() {
            if (chatListener != null) {
                NeoForge.EVENT_BUS.unregister(chatListener);
                chatListener = null;
            }
            if (playerListener != null) {
                NeoForge.EVENT_BUS.unregister(playerListener);
                playerListener = null;
            }
        }

        /**
         * 将 java.util.logging 日志桥接到 SLF4J（NeoForge 日志体系）
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
