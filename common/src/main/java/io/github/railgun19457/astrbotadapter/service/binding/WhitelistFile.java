package io.github.railgun19457.astrbotadapter.service.binding;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 离线模式下的 {@code whitelist.json} 直读直写。
 *
 * <p>为什么不能走 {@code /whitelist add <名字>}：服务端解析玩家名时先查 usercache，未命中就向 Mojang
 * 名字 API 查询，把查到的**正版** UUID 写进白名单；而离线客户端登录用的是
 * {@code UUID.nameUUIDFromBytes("OfflinePlayer:" + name)} 推导的 UUID（v3）。白名单按 UUID 匹配
 * （26.2 的 {@code UserWhiteList.getKeyForUser} 返回 {@code NameAndId.id()} 的字符串），两者不等，
 * 该玩家就永远进不来（{@code You are not white-listed on this server!}）。</p>
 *
 * <p>本类是纯文件读写，不依赖任何平台类，便于单元测试；文件格式与 26.2 的
 * {@code NameAndId.appendTo} 完全一致——顶层数组，每项只有 {@code uuid}（小写带连字符）与 {@code name}。</p>
 */
public final class WhitelistFile {

    /** 一次写入对目标条目造成的实际变化 */
    public enum Change {
        /** 原先没有该名字的条目：本次由绑定写入 */
        ADDED,
        /** 原先有同名条目但 UUID 不符（在线 UUID 污染）：已就地改写为正确 UUID */
        REPLACED,
        /** 原先就有同名且 UUID 正确的条目：非本次写入（管理员手工添加，或此前已修好） */
        PRESENT
    }

    /** whitelist.json 中的一条条目 */
    public record Entry(String name, UUID uuid) {
    }

    private WhitelistFile() {
    }

    /**
     * 离线模式玩家 UUID，与 26.2 的 {@code UUIDUtil.createOfflinePlayerUUID} 等价。
     */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** 读取全部条目（保持文件顺序）；文件缺失或为空视作空数组。 */
    public static List<Entry> read(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return List.of();
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return List.of();
        }

        JsonElement root;
        try {
            root = JsonParser.parseString(content);
        } catch (RuntimeException e) {
            throw new IOException("白名单文件不是合法 JSON: " + file, e);
        }
        if (!root.isJsonArray()) {
            throw new IOException("白名单文件顶层不是数组: " + file);
        }

        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (!object.has("name") || !object.has("uuid")) {
                continue;
            }
            try {
                entries.add(new Entry(object.get("name").getAsString(),
                        UUID.fromString(object.get("uuid").getAsString())));
            } catch (RuntimeException ignored) {
                // 单条坏了不该让整份名单不可用；写回时该条会被丢弃（与 26.2 载入时忽略非法条目的行为一致）
            }
        }
        return entries;
    }

    /** 取同名（忽略大小写）条目的 UUID；没有该名字时返回 null。 */
    public static UUID findUuid(Path file, String name) throws IOException {
        for (Entry entry : read(file)) {
            if (entry.name().equalsIgnoreCase(name)) {
                return entry.uuid();
            }
        }
        return null;
    }

    /**
     * 写入目标条目：不存在则追加，存在但 UUID 不同则就地替换，已存在且 UUID 相同则不动文件。
     *
     * @return 本次写入对目标条目造成的实际变化（{@link Change#PRESENT} 时不写盘）
     */
    public static Change upsert(Path file, String name, UUID uuid) throws IOException {
        List<Entry> entries = new ArrayList<>(read(file));
        int index = indexOf(entries, name);
        if (index >= 0) {
            if (entries.get(index).uuid().equals(uuid)) {
                return Change.PRESENT;
            }
            entries.set(index, new Entry(name, uuid));
            write(file, entries);
            return Change.REPLACED;
        }

        entries.add(new Entry(name, uuid));
        write(file, entries);
        return Change.ADDED;
    }

    /**
     * 移除同名（忽略大小写）条目。
     *
     * <p>优先只删 UUID 与 {@code uuid} 相符的那条；若没有命中（例如解绑的是修复前留下的、
     * 被在线 UUID 污染的老条目），则退化为删除该名字下的全部条目——调用方已确认该条目是由绑定写入的。</p>
     *
     * @return 是否真的改动了文件
     */
    public static boolean remove(Path file, String name, UUID uuid) throws IOException {
        List<Entry> entries = read(file);
        boolean matchedUuid = uuid != null && entries.stream()
                .anyMatch(entry -> entry.name().equalsIgnoreCase(name) && entry.uuid().equals(uuid));
        if (!matchedUuid && entries.stream().noneMatch(entry -> entry.name().equalsIgnoreCase(name))) {
            return false;
        }

        List<Entry> remaining = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            boolean drop = entry.name().equalsIgnoreCase(name)
                    && (!matchedUuid || entry.uuid().equals(uuid));
            if (!drop) {
                remaining.add(entry);
            }
        }
        write(file, remaining);
        return true;
    }

    private static int indexOf(List<Entry> entries, String name) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** 整份重写：换行与缩进沿用 vanilla（Gson pretty printing），字段只保留 uuid 与 name。 */
    private static void write(Path file, List<Entry> entries) throws IOException {
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", entry.uuid().toString());
            object.addProperty("name", entry.name());
            array.add(object);
        }

        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // 先写临时文件再原子替换：避免服务端在写得一半时读到残缺名单
        Path temp = Files.createTempFile(parent, "whitelist", ".json.tmp");
        try {
            Files.writeString(temp, JsonUtil.getGson().toJson(array), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
