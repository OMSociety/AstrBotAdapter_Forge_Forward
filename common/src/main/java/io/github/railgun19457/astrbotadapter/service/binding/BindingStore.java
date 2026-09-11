package io.github.railgun19457.astrbotadapter.service.binding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 绑定关系的持久化存储（bindings.json）。
 *
 * <p>并发模型：读取路径由 {@link BindingService} 串行化（其 operationLock 覆盖整段读改写流程）；
 * 写入路径通过本类的锁与原子落盘保证不产生半截文件。内存里维护两张表：正向按
 * 「账号 + 绑定类型」定位记录，反向按游戏 ID 做全局唯一性校验。</p>
 *
 * <p>主键为 {@link BindingRecord#bindingKey()}，即「平台:用户ID:类型」，因此同一账号可以同时
 * 持有 Java 版与基岩版两条记录。</p>
 */
public final class BindingStore {

    private final Path file;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Object lock = new Object();

    /** bindingKey（平台:用户ID:类型）-> 记录 */
    private final Map<String, BindingRecord> byKey = new LinkedHashMap<>();
    /** 游戏 ID 小写 -> bindingKey（用于全局占用校验） */
    private final Map<String, String> keyByGameName = new LinkedHashMap<>();

    public BindingStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public Path getFile() {
        return file;
    }

    /**
     * 从磁盘载入。文件缺失视为空表；文件损坏则备份为 .bak 后重建空表（不阻断服务器启动）。
     *
     * <p>老数据没有 kind 字段，这里按 floodgate 推断补齐，保证升级不丢绑定。</p>
     */
    public void load() {
        synchronized (lock) {
            byKey.clear();
            keyByGameName.clear();

            if (!Files.exists(file)) {
                return;
            }

            List<BindingRecord> records = null;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                records = gson.fromJson(reader, new TypeToken<List<BindingRecord>>() {
                }.getType());
            } catch (IOException | JsonSyntaxException e) {
                backupCorrupted(e);
            }

            if (records == null) {
                return;
            }

            int skipped = 0;
            int migrated = 0;
            for (BindingRecord record : records) {
                if (record == null || record.getSubjectUserId().isBlank() || record.getGameName().isBlank()) {
                    skipped++;
                    continue;
                }
                // 老数据补齐绑定类型（无 kind 时按 floodgate 推断）
                boolean hadKind = record.getKind() != null && !record.getKind().isBlank();
                record.normalizeKind();
                if (!hadKind) {
                    migrated++;
                }
                putIndex(record);
            }
            if (skipped > 0) {
                logger.warning("绑定数据有 " + skipped + " 条记录缺少必要字段，已忽略");
            }
            if (migrated > 0) {
                logger.info("绑定数据有 " + migrated + " 条旧记录缺少绑定类型，已按基岩版标记自动补齐");
            }
            logger.info("已载入 " + byKey.size() + " 条绑定记录");
        }
    }

    private void backupCorrupted(Exception cause) {
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            logger.warning("绑定数据文件无法解析（" + cause.getMessage() + "），已备份到 "
                    + backup.getFileName() + " 并重建空表");
        } catch (IOException moveFailure) {
            logger.warning("绑定数据文件无法解析且备份失败: " + moveFailure.getMessage());
        }
    }

    /** 写入索引；同名旧索引先清掉，避免同一游戏 ID 残留两条反向映射。 */
    private void putIndex(BindingRecord record) {
        BindingRecord previous = byKey.put(record.bindingKey(), record);
        if (previous != null) {
            keyByGameName.remove(previous.getGameName().toLowerCase(Locale.ROOT));
        }
        keyByGameName.put(record.getGameName().toLowerCase(Locale.ROOT), record.bindingKey());
    }

    /** 精确取某账号某一类绑定（kind 取 java / geyser）。 */
    public BindingRecord get(String platform, String userId, String kind) {
        synchronized (lock) {
            return byKey.get(BindingRecord.bindingKey(platform, userId, kind));
        }
    }

    /** 取某账号的全部绑定（0~2 条：Java 与基岩各一）。 */
    public List<BindingRecord> getAll(String platform, String userId) {
        List<BindingRecord> result = new ArrayList<>(2);
        for (String kind : new String[]{BindingRecord.KIND_JAVA, BindingRecord.KIND_GEYSER}) {
            BindingRecord record = get(platform, userId, kind);
            if (record != null) {
                result.add(record);
            }
        }
        return result;
    }

    /** 按游戏 ID 反查（全局唯一性校验用）。 */
    public BindingRecord getByGameName(String gameName) {
        if (gameName == null || gameName.isBlank()) {
            return null;
        }
        synchronized (lock) {
            String key = keyByGameName.get(gameName.toLowerCase(Locale.ROOT));
            return key == null ? null : byKey.get(key);
        }
    }

    /**
     * 写入或覆盖一条记录（占用校验由调用方在 {@link BindingService} 内完成）。
     * 只影响同账号同类的那条记录，另一类保持不变。
     */
    public void put(BindingRecord record) {
        synchronized (lock) {
            BindingRecord existing = byKey.get(record.bindingKey());
            if (existing != null) {
                record.setCreatedAt(existing.getCreatedAt());
            }
            record.setUpdatedAt(System.currentTimeMillis());
            putIndex(record);
        }
    }

    /** 删除某账号某一类绑定，返回被删除的记录（不存在返回 null）。 */
    public BindingRecord remove(String platform, String userId, String kind) {
        synchronized (lock) {
            String key = BindingRecord.bindingKey(platform, userId, kind);
            BindingRecord removed = byKey.remove(key);
            if (removed != null) {
                keyByGameName.remove(removed.getGameName().toLowerCase(Locale.ROOT));
            }
            return removed;
        }
    }

    /** 删除某账号的全部绑定，返回被删除的记录列表（可能为空）。 */
    public List<BindingRecord> removeAll(String platform, String userId) {
        List<BindingRecord> removed = new ArrayList<>(2);
        for (String kind : new String[]{BindingRecord.KIND_JAVA, BindingRecord.KIND_GEYSER}) {
            BindingRecord record = remove(platform, userId, kind);
            if (record != null) {
                removed.add(record);
            }
        }
        return removed;
    }

    public Collection<BindingRecord> all() {
        synchronized (lock) {
            return new ArrayList<>(byKey.values());
        }
    }

    public int size() {
        synchronized (lock) {
            return byKey.size();
        }
    }

    /**
     * 原子落盘：先写同目录临时文件再 move，避免写入中断留下半截 JSON。
     */
    public void save() {
        List<BindingRecord> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(byKey.values());
        }

        String json = gson.toJson(snapshot);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.warning("保存绑定数据失败: " + e.getMessage());
        }
    }
}
