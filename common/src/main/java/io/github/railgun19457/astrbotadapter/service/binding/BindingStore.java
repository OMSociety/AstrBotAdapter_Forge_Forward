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
 * <p>并发模型：读取路径由 {@link BindingService} 串行化到服务器主线程；写入路径（REST 请求线程）
 * 通过本类的锁与原子落盘保证不产生半截文件。内存索引同时维护正向（subject）与反向（游戏 ID）
 * 两张表，用于「一个游戏 ID 只能被一个外部账号占用」的校验。</p>
 */
public final class BindingStore {

    private final Path file;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Object lock = new Object();

    /** subject -> 记录 */
    private final Map<String, BindingRecord> bySubject = new LinkedHashMap<>();
    /** 游戏 ID 小写 -> subject */
    private final Map<String, String> subjectByGameName = new LinkedHashMap<>();

    public BindingStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public Path getFile() {
        return file;
    }

    /**
     * 从磁盘载入。文件缺失视为空表；文件损坏则备份为 .bak 后重建空表（不阻断服务器启动）。
     */
    public void load() {
        synchronized (lock) {
            bySubject.clear();
            subjectByGameName.clear();

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
            for (BindingRecord record : records) {
                if (record == null || record.getSubjectUserId().isBlank() || record.getGameName().isBlank()) {
                    skipped++;
                    continue;
                }
                putIndex(record);
            }
            if (skipped > 0) {
                logger.warning("绑定数据有 " + skipped + " 条记录缺少必要字段，已忽略");
            }
            logger.info("已载入 " + bySubject.size() + " 条绑定记录");
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

    private void putIndex(BindingRecord record) {
        bySubject.put(record.subjectKey(), record);
        subjectByGameName.put(record.getGameName().toLowerCase(Locale.ROOT), record.subjectKey());
    }

    public BindingRecord getBySubject(String platform, String userId) {
        synchronized (lock) {
            return bySubject.get(BindingRecord.subjectKey(platform, userId));
        }
    }

    public BindingRecord getByGameName(String gameName) {
        if (gameName == null || gameName.isBlank()) {
            return null;
        }
        synchronized (lock) {
            String subject = subjectByGameName.get(gameName.toLowerCase(Locale.ROOT));
            return subject == null ? null : bySubject.get(subject);
        }
    }

    /**
     * 写入或覆盖一条记录（已存在同名记录占用校验由调用方在 {@link BindingService} 内完成）。
     */
    public void put(BindingRecord record) {
        synchronized (lock) {
            BindingRecord existing = bySubject.get(record.subjectKey());
            if (existing != null) {
                subjectByGameName.remove(existing.getGameName().toLowerCase(Locale.ROOT));
                record.setCreatedAt(existing.getCreatedAt());
            }
            record.setUpdatedAt(System.currentTimeMillis());
            putIndex(record);
        }
    }

    /**
     * 删除绑定，返回被删除的记录（不存在返回 null）。
     */
    public BindingRecord remove(String platform, String userId) {
        synchronized (lock) {
            String key = BindingRecord.subjectKey(platform, userId);
            BindingRecord removed = bySubject.remove(key);
            if (removed != null) {
                subjectByGameName.remove(removed.getGameName().toLowerCase(Locale.ROOT));
            }
            return removed;
        }
    }

    public Collection<BindingRecord> all() {
        synchronized (lock) {
            return new ArrayList<>(bySubject.values());
        }
    }

    public int size() {
        synchronized (lock) {
            return bySubject.size();
        }
    }

    /**
     * 原子落盘：先写同目录临时文件再 move，避免写入中断留下半截 JSON。
     */
    public void save() {
        List<BindingRecord> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(bySubject.values());
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
