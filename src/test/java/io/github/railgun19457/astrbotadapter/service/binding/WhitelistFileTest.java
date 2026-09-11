package io.github.railgun19457.astrbotadapter.service.binding;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线 UUID 推导与 whitelist.json 读改写的单元测试。
 *
 * <p>三个玩家名向量取自真实服务器的实测数据：Mojang 名字 API 返回的正版 UUID 与离线推导的 UUID
 * 不一致，正是本次修复要解决的根因。</p>
 */
class WhitelistFileTest {

    /** 实测 Mojang 名字 API 对 MakurokiTomoko 返回的正版 UUID（修复前被写进白名单的那个） */
    private static final UUID MOJANG_MAKUROKI =
            UUID.fromString("38644dc6-6269-465d-b4fb-744d2e529c98");

    @Test
    @DisplayName("离线 UUID 推导与真实服务器向量一致")
    void offlineUuidMatchesRealVectors() {
        assertEquals(UUID.fromString("0e4fa584-40f9-329a-b5af-23593dab24af"),
                WhitelistFile.offlineUuid("Slandre"));
        assertEquals(UUID.fromString("cd2a40c7-973c-321d-b7d2-c932cdffcc0d"),
                WhitelistFile.offlineUuid("MakurokiTomoko"));
        assertEquals(UUID.fromString("73e5d809-d916-3038-8dda-a0d4a93b669d"),
                WhitelistFile.offlineUuid("makuroki_tomoko"));
    }

    @Test
    @DisplayName("离线 UUID 与 Mojang 的正版 UUID 不同")
    void offlineUuidDiffersFromMojangUuid() {
        assertFalse(MOJANG_MAKUROKI.equals(WhitelistFile.offlineUuid("MakurokiTomoko")));
    }

    @Test
    @DisplayName("文件缺失或为空按空名单处理")
    void missingOrEmptyFileIsTreatedAsEmptyList(@TempDir Path dir) throws IOException {
        Path missing = dir.resolve("whitelist.json");
        assertEquals(List.of(), WhitelistFile.read(missing));
        assertNull(WhitelistFile.findUuid(missing, "Slandre"));

        Path empty = dir.resolve("empty.json");
        Files.writeString(empty, "", StandardCharsets.UTF_8);
        assertEquals(List.of(), WhitelistFile.read(empty));

        Path blank = dir.resolve("blank.json");
        Files.writeString(blank, "\n  \n", StandardCharsets.UTF_8);
        assertEquals(List.of(), WhitelistFile.read(blank));
    }

    @Test
    @DisplayName("新条目写成顶层数组，每项只有 uuid 与 name")
    void newEntryIsWrittenAsFlatArray(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        UUID uuid = WhitelistFile.offlineUuid("Slandre");

        assertEquals(WhitelistFile.Change.ADDED, WhitelistFile.upsert(file, "Slandre", uuid));

        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(root.isJsonArray(), "whitelist.json 顶层必须是数组");
        JsonArray array = root.getAsJsonArray();
        assertEquals(1, array.size());
        JsonObject entry = array.get(0).getAsJsonObject();
        assertEquals(2, entry.size(), "每个条目只能有 uuid 与 name 两个字段");
        assertEquals("0e4fa584-40f9-329a-b5af-23593dab24af", entry.get("uuid").getAsString(),
                "uuid 必须是小写带连字符形式");
        assertEquals("Slandre", entry.get("name").getAsString());

        assertEquals(uuid, WhitelistFile.findUuid(file, "Slandre"));
    }

    @Test
    @DisplayName("已有同名但 UUID 不同的条目被替换成正确值")
    void entryWithWrongUuidIsReplaced(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        WhitelistFile.upsert(file, "MakurokiTomoko", MOJANG_MAKUROKI);

        UUID offline = WhitelistFile.offlineUuid("MakurokiTomoko");
        assertEquals(WhitelistFile.Change.REPLACED,
                WhitelistFile.upsert(file, "MakurokiTomoko", offline));

        List<WhitelistFile.Entry> entries = WhitelistFile.read(file);
        assertEquals(1, entries.size(), "应当是替换而不是追加");
        assertEquals(offline, entries.get(0).uuid());
        assertEquals("MakurokiTomoko", entries.get(0).name());
    }

    @Test
    @DisplayName("已有同名且 UUID 正确的条目视为管理员添加，不改动文件")
    void entryWithSameUuidIsKept(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        UUID uuid = WhitelistFile.offlineUuid("Slandre");
        WhitelistFile.upsert(file, "Slandre", uuid);
        String before = Files.readString(file, StandardCharsets.UTF_8);

        assertEquals(WhitelistFile.Change.PRESENT, WhitelistFile.upsert(file, "Slandre", uuid));

        assertEquals(before, Files.readString(file, StandardCharsets.UTF_8),
                "已存在的正确条目不应被改写");
        assertEquals(1, WhitelistFile.read(file).size());
    }

    @Test
    @DisplayName("名字比较忽略大小写")
    void nameMatchingIsCaseInsensitive(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        UUID uuid = WhitelistFile.offlineUuid("Slandre");
        WhitelistFile.upsert(file, "Slandre", uuid);

        assertEquals(uuid, WhitelistFile.findUuid(file, "slandre"));
        assertEquals(WhitelistFile.Change.PRESENT, WhitelistFile.upsert(file, "SLANDRE", uuid));
        assertEquals(1, WhitelistFile.read(file).size());
    }

    @Test
    @DisplayName("多条目写入后顺序保持且不丢条目")
    void existingOrderIsPreserved(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        Files.writeString(file, """
                [
                  {
                    "uuid": "11111111-1111-3111-8111-111111111111",
                    "name": "Alpha"
                  },
                  {
                    "uuid": "22222222-2222-3222-8222-222222222222",
                    "name": "Beta"
                  }
                ]
                """, StandardCharsets.UTF_8);

        UUID slandre = WhitelistFile.offlineUuid("Slandre");
        assertEquals(WhitelistFile.Change.ADDED, WhitelistFile.upsert(file, "Slandre", slandre));

        List<WhitelistFile.Entry> entries = WhitelistFile.read(file);
        assertEquals(3, entries.size());
        assertEquals("Alpha", entries.get(0).name());
        assertEquals("Beta", entries.get(1).name());
        assertEquals("Slandre", entries.get(2).name());
        assertEquals(slandre, entries.get(2).uuid());
    }

    @Test
    @DisplayName("按 UUID 删除只删目标条目")
    void removeDeletesOnlyTargetEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        UUID slandre = WhitelistFile.offlineUuid("Slandre");
        WhitelistFile.upsert(file, "Alpha", UUID.fromString("11111111-1111-3111-8111-111111111111"));
        WhitelistFile.upsert(file, "Slandre", slandre);

        assertTrue(WhitelistFile.remove(file, "Slandre", slandre));

        List<WhitelistFile.Entry> entries = WhitelistFile.read(file);
        assertEquals(1, entries.size());
        assertEquals("Alpha", entries.get(0).name());
        assertFalse(WhitelistFile.remove(file, "Slandre", slandre),
                "条目已不在文件里时应返回 false");
    }

    @Test
    @DisplayName("修复上线前遗留的被污染老条目也能删掉")
    void removeFallsBackToNameForPollutedEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        WhitelistFile.upsert(file, "MakurokiTomoko", MOJANG_MAKUROKI);

        assertTrue(WhitelistFile.remove(file, "MakurokiTomoko",
                WhitelistFile.offlineUuid("MakurokiTomoko")));

        assertEquals(List.of(), WhitelistFile.read(file));
    }

    @Test
    @DisplayName("同一名字出现重复条目时只删 UUID 命中的那条，不碰别人的条目")
    void removeKeepsForeignDuplicateOfSameName(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        UUID ours = WhitelistFile.offlineUuid("Duplicate");
        WhitelistFile.upsert(file, "Duplicate", ours);
        String withDuplicate = Files.readString(file, StandardCharsets.UTF_8).replaceFirst(
                "\\]\\s*$",
                ",\n  {\n    \"uuid\": \"33333333-3333-3333-8333-333333333333\",\n"
                        + "    \"name\": \"Duplicate\"\n  }\n]");
        Files.writeString(file, withDuplicate, StandardCharsets.UTF_8);
        assertEquals(2, WhitelistFile.read(file).size());

        assertTrue(WhitelistFile.remove(file, "Duplicate", ours));

        List<WhitelistFile.Entry> remaining = WhitelistFile.read(file);
        assertEquals(1, remaining.size(), "同名但 UUID 不是我们写入的那条必须保留");
        assertEquals(UUID.fromString("33333333-3333-3333-8333-333333333333"), remaining.get(0).uuid());
    }

    @Test
    @DisplayName("非法 JSON 不静默清空而是报错")
    void malformedJsonThrowsInsteadOfWipingFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> WhitelistFile.read(file));
    }

    @Test
    @DisplayName("顶层不是数组时报错")
    void nonArrayRootThrows(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("whitelist.json");
        Files.writeString(file, "{\"uuid\":\"x\"}", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> WhitelistFile.read(file));
    }
}
