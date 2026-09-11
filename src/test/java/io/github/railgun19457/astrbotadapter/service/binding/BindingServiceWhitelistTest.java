package io.github.railgun19457.astrbotadapter.service.binding;

import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.PlatformType;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 绑定流程在白名单上的行为测试：离线模式必须写入本地推导的离线 UUID，且 {@code whitelistAdded}
 * 归属要分清「本次绑定写入」与「管理员手工添加」。基岩版必须**回读校验**白名单，避免 Floodgate
 * 名字解析失败时谎报成功。
 */
class BindingServiceWhitelistTest {

    /** 实测自真实服务器的基岩玩家 Floodgate UUID（.Slandre5167） */
    private static final UUID FLOODGATE_UID =
            UUID.fromString("00000000-0000-0000-0009-01f3e2933322");

    @Test
    @DisplayName("离线模式：按名字推导离线 UUID 写入白名单并重载，不再走 whitelist add")
    void offlineModeWritesOfflineUuid(@TempDir Path dir) throws IOException {
        FakeAdapter adapter = new FakeAdapter(dir.resolve("whitelist.json"), false);
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10001", "MakurokiTomoko", false);

        assertTrue(result.isSuccess(), "绑定应当成功");
        assertTrue(result.getRecord().isWhitelistAdded(), "条目由本次绑定写入");
        assertEquals("cd2a40c7-973c-321d-b7d2-c932cdffcc0d", result.getRecord().getJavaUuid());
        assertEquals(WhitelistFile.offlineUuid("MakurokiTomoko"),
                WhitelistFile.findUuid(adapter.whitelistFile, "MakurokiTomoko"));
        assertTrue(adapter.executed.isEmpty(), "离线模式下不该执行 whitelist add");
        assertTrue(adapter.reloads > 0, "写完文件必须重载内存名单");
    }

    @Test
    @DisplayName("离线模式：被污染的正版 UUID 条目被自动改写成离线 UUID")
    void offlineModeRepairsPollutedEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        Files.writeString(file, """
                [
                  {
                    "uuid": "38644dc6-6269-465d-b4fb-744d2e529c98",
                    "name": "MakurokiTomoko"
                  }
                ]
                """, StandardCharsets.UTF_8);
        FakeAdapter adapter = new FakeAdapter(file, false);
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10001", "MakurokiTomoko", false);

        assertTrue(result.isSuccess());
        assertTrue(result.getRecord().isWhitelistAdded(), "被修正的条目算本次绑定写入");
        assertEquals(WhitelistFile.offlineUuid("MakurokiTomoko"),
                WhitelistFile.findUuid(file, "MakurokiTomoko"));
        assertEquals(1, WhitelistFile.read(file).size(), "修正后仍只有一条");
    }

    @Test
    @DisplayName("离线模式：管理员手工添加的正确条目保留，解绑时不被删除")
    void offlineModeKeepsAdminAddedEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        UUID offline = WhitelistFile.offlineUuid("Slandre");
        WhitelistFile.upsert(file, "Slandre", offline);
        FakeAdapter adapter = new FakeAdapter(file, false);
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10002", "Slandre", false);
        assertTrue(result.isSuccess(), "已有正确条目时绑定照样成功");
        assertFalse(result.getRecord().isWhitelistAdded(), "该条目不是本次绑定写入");

        BindingService.Result unbound = service.unbind("qq", "10002", BindingRecord.KIND_JAVA);
        assertTrue(unbound.isSuccess());
        assertEquals(offline, WhitelistFile.findUuid(file, "Slandre"), "管理员添加的条目必须保留");
    }

    @Test
    @DisplayName("离线模式：解绑删除由绑定写入的条目（按离线 UUID）并重载")
    void offlineModeUnbindRemovesOwnEntry(@TempDir Path dir) throws IOException {
        FakeAdapter adapter = new FakeAdapter(dir.resolve("whitelist.json"), false);
        BindingService service = newService(dir, adapter);

        assertTrue(service.bind("qq", "10003", "Slandre", false).isSuccess());
        int reloadsAfterBind = adapter.reloads;

        BindingService.Result unbound = service.unbind("qq", "10003", BindingRecord.KIND_JAVA);

        assertTrue(unbound.isSuccess());
        assertEquals(List.of(), WhitelistFile.read(adapter.whitelistFile));
        assertTrue(adapter.reloads > reloadsAfterBind, "删除后同样要重载");
    }

    @Test
    @DisplayName("正版验证开启：走 whitelist add 指令")
    void onlineModeUsesCommand(@TempDir Path dir) {
        FakeAdapter adapter = new FakeAdapter(dir.resolve("whitelist.json"), true);
        BindingService service = newService(dir, adapter);

        BindingService.Result added = service.bind("qq", "10004", "Slandre", false);

        assertTrue(added.isSuccess());
        assertTrue(added.getRecord().isWhitelistAdded());
        assertEquals(List.of("whitelist add Slandre"), adapter.executed);
    }

    @Test
    @DisplayName("正版验证开启：白名单里已有该名字时判为非本次写入")
    void onlineModeTreatsExistingEntryAsAdminAdded(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        WhitelistFile.upsert(file, "Slandre",
                UUID.fromString("0e4fa584-40f9-329a-b5af-23593dab24af"));
        FakeAdapter adapter = new FakeAdapter(file, true);
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10005", "Slandre", false);

        assertTrue(result.isSuccess());
        assertFalse(result.getRecord().isWhitelistAdded(), "已有条目不算本次绑定写入");
        assertEquals(List.of("whitelist add Slandre"), adapter.executed, "仍按原路径执行指令");
    }

    @Test
    @DisplayName("基岩版：交给 Floodgate 的 fwhitelist，名字不带 Geyser 前缀")
    void geyserUsesFloodgateCommand(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        // fwhitelist 成功时会把「名字 + Floodgate UUID」写进 whitelist.json；本测试模拟这一结果，
        // 用于验证插件的写后回读校验会读到它。
        WhitelistFile.upsert(file, "MakurokiTomoko", FLOODGATE_UID);
        FakeAdapter adapter = new FakeAdapter(file, false);
        adapter.floodgate = true;
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10006", "MakurokiTomoko", true);

        assertTrue(result.isSuccess());
        assertTrue(result.getRecord().isWhitelistAdded(), "回读到正确条目才算本次写入");
        assertEquals(List.of("fwhitelist add MakurokiTomoko"), adapter.executed);
        assertEquals(".MakurokiTomoko", result.getRecord().getGameName(), "记录里仍保留带前缀的游戏名");
    }

    @Test
    @DisplayName("基岩版：fwhitelist 没写进白名单时判失败，不再谎报成功")
    void geyserRejectsWhenFloodgateDidNotWrite(@TempDir Path dir) {
        FakeAdapter adapter = new FakeAdapter(dir.resolve("whitelist.json"), false);
        adapter.floodgate = true;
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10008", "ikarMing", true);

        // 这正是线上事故：fwhitelist 只打印 "Unable to find user in our cache"、不改白名单，
        // 而 executeCommand 恒返回 true。必须判失败，否则玩家永远被白名单拒绝。
        assertFalse(result.isSuccess(), "白名单没写成必须判失败");
        assertEquals(BindingService.Rejection.WHITELIST_FAILED, result.getRejection());
    }

    @Test
    @DisplayName("基岩版：白名单里是离线 UUID（历史坏条目）时判失败")
    void geyserRejectsWrongUuid(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        WhitelistFile.upsert(file, "ikarMing", WhitelistFile.offlineUuid("ikarMing"));
        FakeAdapter adapter = new FakeAdapter(file, false);
        adapter.floodgate = true;
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10009", "ikarMing", true);

        assertFalse(result.isSuccess(), "名字在册但 UUID 不是 Floodgate 身份，同样进不去，必须判失败");
        assertEquals(BindingService.Rejection.WHITELIST_FAILED, result.getRejection());
    }

    @Test
    @DisplayName("基岩版：玩家在线时用其真实 Floodgate UUID 直写白名单")
    void geyserOnlinePlayerWritesRealUuid(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        FakeAdapter adapter = new FakeAdapter(file, false);
        adapter.floodgate = true;
        adapter.onlinePlayerName = ".ikarMing";
        adapter.onlinePlayerUuid = UUID.fromString("00000000-0000-0000-0009-01fdc1b824e8");
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10010", "ikarMing", true);

        assertTrue(result.isSuccess());
        assertEquals(adapter.onlinePlayerUuid, WhitelistFile.findUuid(file, ".ikarMing"),
                "必须写玩家真实的 Floodgate UUID");
        assertTrue(adapter.executed.isEmpty(), "玩家在线时不该再依赖 fwhitelist 的名字解析");
    }

    @Test
    @DisplayName("基岩版：没有 Floodgate 时退回 whitelist add")
    void geyserFallsBackWithoutFloodgate(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        WhitelistFile.upsert(file, "Slandre", FLOODGATE_UID);
        FakeAdapter adapter = new FakeAdapter(file, false);
        BindingService service = newService(dir, adapter);

        BindingService.Result result = service.bind("qq", "10007", "Slandre", true);

        assertTrue(result.isSuccess());
        assertEquals(List.of("whitelist add Slandre"), adapter.executed);
    }

    private BindingService newService(Path dir, FakeAdapter adapter) {
        PluginConfig config = new PluginConfig();
        config.setBindingEnabled(true);
        Logger logger = Logger.getLogger("BindingServiceWhitelistTest");
        BindingStore store = new BindingStore(dir.resolve("bindings.json"), logger);
        store.load();
        return new BindingService(config, adapter, store, logger);
    }

    /** 只实现绑定流程用到的那部分能力；其余保持空实现。 */
    private static final class FakeAdapter implements PlatformAdapter {

        private final Path whitelistFile;
        private final boolean onlineMode;
        private final List<String> executed = new ArrayList<>();
        private boolean floodgate;
        private int reloads;
        private int whitelistReads;
        /** 非 null 时表示该名字的玩家在线，{@link #getPlayer(String)} 会返回它 */
        private String onlinePlayerName;
        private UUID onlinePlayerUuid;

        private FakeAdapter(Path whitelistFile, boolean onlineMode) {
            this.whitelistFile = whitelistFile;
            this.onlineMode = onlineMode;
        }

        @Override
        public boolean isOnlineMode() {
            return onlineMode;
        }

        @Override
        public Path getWhitelistFile() {
            whitelistReads++;
            return whitelistFile;
        }

        @Override
        public boolean reloadWhitelist() {
            reloads++;
            return true;
        }

        @Override
        public boolean isFloodgateAvailable() {
            return floodgate;
        }

        @Override
        public boolean executeCommand(String command) {
            executed.add(command);
            return true;
        }

        @Override
        public boolean executeCommand(CommonPlayer player, String command) {
            return false;
        }

        @Override
        public Optional<CommonPlayer> getPlayer(String name) {
            if (onlinePlayerName == null || !onlinePlayerName.equalsIgnoreCase(name)) {
                return Optional.empty();
            }
            String resolvedName = onlinePlayerName;
            UUID resolvedUuid = onlinePlayerUuid;
            return Optional.of(new CommonPlayer() {
                @Override
                public UUID getUniqueId() {
                    return resolvedUuid;
                }

                @Override
                public String getName() {
                    return resolvedName;
                }

                @Override
                public String getDisplayName() {
                    return resolvedName;
                }

                @Override
                public int getPing() {
                    return 0;
                }

                @Override
                public void sendMessage(String message) {
                }

                @Override
                public boolean hasPermission(String permission) {
                    return false;
                }

                @Override
                public boolean isOnline() {
                    return true;
                }
            });
        }

        @Override
        public Optional<CommonPlayer> getPlayer(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public PlatformType getPlatformType() {
            return PlatformType.UNKNOWN;
        }

        @Override
        public String getServerVersion() {
            return "test";
        }

        @Override
        public String getServerMotd() {
            return "test";
        }

        @Override
        public long getServerUptime() {
            return 0L;
        }

        @Override
        public String getServerName() {
            return "test";
        }

        @Override
        public CommonServer getServer() {
            return null;
        }

        @Override
        public Collection<CommonPlayer> getOnlinePlayers() {
            return List.of();
        }

        @Override
        public int getOnlinePlayerCount() {
            return 0;
        }

        @Override
        public int getMaxPlayers() {
            return 0;
        }

        @Override
        public void broadcastMessage(String message) {
        }

        @Override
        public void sendMessage(CommonPlayer player, String message) {
        }

        @Override
        public void sendConsoleMessage(String message) {
        }

        @Override
        public CommonScheduler getScheduler() {
            return null;
        }

        @Override
        public List<String> getRecentLogs(int lines) {
            return List.of();
        }

        @Override
        public List<String> getLogsByTimeRange(long startTime, long endTime) {
            return List.of();
        }

        @Override
        public void initialize() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void registerListeners() {
        }

        @Override
        public void unregisterListeners() {
        }
    }
}
