package io.github.railgun19457.astrbotadapter.service.binding;

import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 外部账号（QQ 等）与游戏 ID 的绑定服务，并把结果落到服务器白名单。
 *
 * <p>设计取舍：白名单写入**不**复用 {@code /api/v1/command/execute}，而是走本服务的专用入口。
 * 原因是默认配置把 {@code whitelist *} 列在外部指令黑名单里——复用通用指令通道等于给机器人
 * 开放白名单权限；本服务的语义被收窄为「把某个名字加进白名单」，无法执行任意指令。</p>
 *
 * <p>并发：所有 Minecraft API 调用与索引变更都在服务器主线程执行
 * （{@link PlatformAdapter#executeCommand(String)} 内部会把调用调度回主线程并阻塞等待），
 * 因此不存在索引与游戏状态不一致的窗口。</p>
 */
public final class BindingService {

    /** Minecraft 用户名合法字符（Java 版字母数字下划线，基岩版/Floodgate 另含点号） */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\u4e00-\\u9fa5]{1,32}$");

    private final PluginConfig config;
    private final PlatformAdapter platformAdapter;
    private final BindingStore store;
    private final Logger logger;

    /**
     * 串行化整段绑定/解绑流程。
     *
     * <p>每个 BindingStore 方法各自加锁只能保护单次读写；而「查重 → 写白名单（阻塞）→ 落盘」
     * 是一个复合操作。两个 REST 请求（Netty 线程）同时用不同账号绑同一个游戏 ID 时，
     * 若不加这把锁，双方都会通过占用检查，后写入者覆盖索引，先写入者的白名单条目变成孤儿。</p>
     */
    private final java.util.concurrent.locks.ReentrantLock operationLock =
            new java.util.concurrent.locks.ReentrantLock();

    public BindingService(PluginConfig config, PlatformAdapter platformAdapter,
                          BindingStore store, Logger logger) {
        this.config = config;
        this.platformAdapter = platformAdapter;
        this.store = store;
        this.logger = logger;
    }

    public BindingStore getStore() {
        return store;
    }

    public boolean isEnabled() {
        return config.isBindingEnabled();
    }

    // ===== 对外操作 =====

    /**
     * 绑定外部账号到游戏 ID；必要时写入白名单。
     *
     * @param bedrock 基岩版（Geyser/Floodgate）标记，影响名称解析与是否补前缀
     */
    public Result bind(String platform, String userId, String rawName, boolean bedrock) {
        operationLock.lock();
        try {
            return bindLocked(platform, userId, rawName, bedrock);
        } finally {
            operationLock.unlock();
        }
    }

    private Result bindLocked(String platform, String userId, String rawName, boolean bedrock) {
        if (!isEnabled()) {
            return Result.rejected(Rejection.FEATURE_DISABLED, null);
        }

        String platformKey = platform == null ? "" : platform.trim();
        String userKey = userId == null ? "" : userId.trim();
        if (platformKey.isEmpty() || userKey.isEmpty()) {
            return Result.rejected(Rejection.PARAM_MISSING, null);
        }

        String typedName = rawName == null ? "" : rawName.trim();
        if (!NAME_PATTERN.matcher(typedName).matches()) {
            return Result.rejected(Rejection.INVALID_NAME, null);
        }

        // 解析实际生效的游戏 ID：优先用在线玩家（拿到真实大小写与前缀形态），
        // 离线时按 Floodgate 前缀规则推导，保证「绑定即写入」在玩家不在线时也可用。
        ResolvedName resolved = resolveName(typedName, bedrock);

        BindingRecord occupant = store.getByGameName(resolved.name());
        if (occupant != null
                && !occupant.subjectKey().equals(BindingRecord.subjectKey(platformKey, userKey))) {
            // 只回显被占用的游戏 ID，不回显对方账号 ID（隐私）
            return Result.rejected(Rejection.NAME_TAKEN, resolved.name());
        }

        BindingRecord existing = store.getBySubject(platformKey, userKey);
        if (existing != null && !existing.getGameName().equalsIgnoreCase(resolved.name())) {
            // 改绑：先按解绑语义回收旧条目，避免同名条目无限堆积
            releaseWhitelistEntry(existing, platformKey, userKey);
        }

        BindingRecord record = existing == null
                ? new BindingRecord(platformKey, userKey, resolved.name())
                : existing;
        record.setGameName(resolved.name());
        record.setFloodgate(resolved.floodgate());
        record.setJavaUuid(resolved.uuid() == null ? "" : resolved.uuid().toString());

        boolean whitelistAdded = false;
        if (config.isBindingApplyToWhitelist()) {
            whitelistAdded = applyWhitelistAdd(resolved.name());
            if (!whitelistAdded) {
                return Result.rejected(Rejection.WHITELIST_FAILED, resolved.name());
            }
        }
        // 白名单里本来就有同名条目时，保持原有「非本绑定写入」的归属，解绑时不动它
        if (existing == null || !existing.isWhitelistAdded()) {
            record.setWhitelistAdded(whitelistAdded);
        }

        store.put(record);
        store.save();
        logger.info("已绑定 " + BindingRecord.subjectKey(platformKey, userKey) + " -> " + resolved.name()
                + (bedrock ? "（基岩版）" : "") + (whitelistAdded ? "，已加入白名单" : ""));
        return Result.ok(record, true);
    }

    /**
     * 解除绑定；仅移除「由绑定写入」的白名单条目。
     */
    public Result unbind(String platform, String userId) {
        operationLock.lock();
        try {
            return unbindLocked(platform, userId);
        } finally {
            operationLock.unlock();
        }
    }

    private Result unbindLocked(String platform, String userId) {
        if (!isEnabled()) {
            return Result.rejected(Rejection.FEATURE_DISABLED, null);
        }

        BindingRecord record = store.getBySubject(platform, userId);
        if (record == null) {
            return Result.rejected(Rejection.NOT_BOUND, null);
        }

        boolean whitelistRemoved = releaseWhitelistEntry(record, platform, userId);
        store.remove(platform, userId);
        store.save();
        logger.info("已解绑 " + record.subjectKey() + "（原游戏 ID: " + record.getGameName() + "）"
                + (whitelistRemoved ? "，已从白名单移除" : "，白名单条目保留"));
        return Result.ok(record, whitelistRemoved);
    }

    public BindingRecord lookup(String platform, String userId) {
        return store.getBySubject(platform, userId);
    }

    public Collection<BindingRecord> list() {
        return store.all();
    }

    // ===== 名称解析与白名单 =====

    /**
     * 决定最终写入白名单的游戏 ID。
     *
     * <p>在线优先：改名/前缀形态以服务器实际看到的为准。离线时若启用 Floodgate 前缀且输入未带前缀，
     * 则补上前缀（Floodgate use-prefix=true 的默认形态）。</p>
     */
    ResolvedName resolveName(String typedName, boolean bedrock) {
        for (String candidate : nameCandidates(typedName)) {
            Optional<CommonPlayer> online = platformAdapter.getPlayer(candidate);
            if (online.isPresent()) {
                CommonPlayer player = online.get();
                return new ResolvedName(player.getName(), player.getUniqueId(), bedrock);
            }
        }

        String finalName = typedName;
        if (bedrock && config.isGeyserEnabled() && config.isGeyserUsePrefix()) {
            String prefix = config.getGeyserPrefix() == null ? "" : config.getGeyserPrefix();
            if (!prefix.isEmpty() && !typedName.startsWith(prefix)) {
                finalName = prefix + typedName;
            }
        }
        return new ResolvedName(finalName, null, bedrock);
    }

    /**
     * 候选名称列表：兼容 Floodgate 的带/不带前缀两种写法。
     */
    List<String> nameCandidates(String typedName) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(typedName);

        String prefix = config.getGeyserPrefix() == null ? "" : config.getGeyserPrefix();
        if (config.isGeyserEnabled() && !prefix.isEmpty()) {
            if (typedName.startsWith(prefix)) {
                // 用户已带前缀：再试去掉前缀的形态
                String stripped = typedName.substring(prefix.length());
                if (!stripped.isEmpty()) {
                    candidates.add(stripped);
                }
            } else {
                candidates.add(prefix + typedName);
            }
        }
        return new ArrayList<>(candidates);
    }

    private boolean applyWhitelistAdd(String name) {
        boolean success = platformAdapter.executeCommand("whitelist add " + name);
        if (!success) {
            logger.warning("加入白名单失败: " + name + "（请确认服务器已开启 white-list，且指令未被其它插件拦截）");
            return false;
        }
        return true;
    }

    /**
     * 仅在「该条目由绑定写入」时移除白名单，避免误删管理员手工添加的同名条目。
     */
    private boolean releaseWhitelistEntry(BindingRecord record, String platform, String userId) {
        if (record == null || !record.isWhitelistAdded()) {
            return false;
        }
        boolean success = platformAdapter.executeCommand("whitelist remove " + record.getGameName());
        if (!success) {
            logger.warning("从白名单移除失败: " + record.getGameName());
        }
        return success;
    }

    // ===== 结果类型 =====

    /** 拒绝原因（供上层翻译成 HTTP 错误码与提示文案） */
    public enum Rejection {
        FEATURE_DISABLED,
        PARAM_MISSING,
        INVALID_NAME,
        NAME_TAKEN,
        NOT_BOUND,
        WHITELIST_FAILED
    }

    public static final class Result {
        private final boolean success;
        private final Rejection rejection;
        private final BindingRecord record;
        private final boolean whitelistChanged;
        private final String gameName;

        private Result(boolean success, Rejection rejection, BindingRecord record,
                       boolean whitelistChanged, String gameName) {
            this.success = success;
            this.rejection = rejection;
            this.record = record;
            this.whitelistChanged = whitelistChanged;
            this.gameName = gameName;
        }

        static Result ok(BindingRecord record, boolean whitelistChanged) {
            return new Result(true, null, record, whitelistChanged, record.getGameName());
        }

        static Result rejected(Rejection rejection, String gameName) {
            return new Result(false, rejection, null, false, gameName);
        }

        public boolean isSuccess() {
            return success;
        }

        public Rejection getRejection() {
            return rejection;
        }

        public BindingRecord getRecord() {
            return record;
        }

        /** 本次操作是否实际改动了白名单 */
        public boolean isWhitelistChanged() {
            return whitelistChanged;
        }

        /** 涉及的游戏 ID（失败时用于提示被占用的名字） */
        public String getGameName() {
            return gameName;
        }
    }

    /** 解析后的游戏 ID */
    record ResolvedName(String name, UUID uuid, boolean floodgate) {
    }
}
