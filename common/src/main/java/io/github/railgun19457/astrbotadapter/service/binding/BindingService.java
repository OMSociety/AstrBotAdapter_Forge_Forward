package io.github.railgun19457.astrbotadapter.service.binding;

import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 外部账号（QQ 等）与游戏 ID 的绑定服务，并把结果落到服务器白名单。
 *
 * <p>一个外部账号可以**同时**持有一条 Java 版绑定与一条基岩版绑定，两条白名单条目并存、互不覆盖。
 * 因此所有「按账号定位记录」的操作都带绑定类型（{@code java} / {@code geyser}）。</p>
 *
 * <p>设计取舍：白名单写入**不**复用 {@code /api/v1/command/execute}，而是走本服务的专用入口。
 * 原因是默认配置把 {@code whitelist *} 列在外部指令黑名单里——复用通用指令通道等于给机器人
 * 开放白名单权限；本服务的语义被收窄为「把某个名字加进白名单」，无法执行任意指令。</p>
 *
 * <p>并发：{@link #operationLock} 串行化整段读改写流程（含阻塞的白名单指令），
 * 避免两个请求同时通过占用校验。</p>
 */
public final class BindingService {

    /** Minecraft 用户名合法字符（Java 版字母数字下划线，基岩版/Floodgate 另含点号） */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\u4e00-\\u9fa5]{1,32}$");

    /** 按稳定顺序遍历两种绑定类型，保证日志与响应顺序可预期 */
    private static final String[] ALL_KINDS = {BindingRecord.KIND_JAVA, BindingRecord.KIND_GEYSER};

    private final PluginConfig config;
    private final PlatformAdapter platformAdapter;
    private final BindingStore store;
    private final Logger logger;

    /**
     * 串行化整段绑定/解绑流程。
     *
     * <p>每个 BindingStore 方法各自加锁只能保护单次读写；而「查重 → 写白名单（阻塞）→ 落盘」
     * 是一个复合操作。两个 REST 请求同时绑同一个游戏 ID 时，若不加这把锁，双方都会通过占用
     * 检查，后写入者覆盖索引，先写入者的白名单条目变成孤儿。</p>
     */
    private final ReentrantLock operationLock = new ReentrantLock();

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

    /** 绑定（只影响与 {@code bedrock} 对应的那一条：Java 或基岩，另一条保持不变）。 */
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

        String kind = BindingRecord.kindOf(bedrock);

        // 解析实际生效的游戏 ID：优先用在线玩家（拿到真实大小写与前缀形态），
        // 离线时按 Floodgate 前缀规则推导，保证「绑定即写入」在玩家不在线时也可用。
        ResolvedName resolved = resolveName(typedName, bedrock);

        // 全局占用校验：同一游戏 ID 只能属于一个账号（跨 Java/基岩也拦）
        BindingRecord occupant = store.getByGameName(resolved.name());
        if (occupant != null
                && !occupant.bindingKey().equals(BindingRecord.bindingKey(platformKey, userKey, kind))) {
            // 只回显被占用的游戏 ID，不回显对方账号 ID（隐私）
            return Result.rejected(Rejection.NAME_TAKEN, resolved.name());
        }

        // 只回收**同一类型**的旧条目：改绑 Java 名不应影响基岩那条，反之亦然
        BindingRecord existing = store.get(platformKey, userKey, kind);
        boolean sameName = existing != null && existing.getGameName().equalsIgnoreCase(resolved.name());
        if (existing != null && !sameName) {
            releaseWhitelistEntry(existing);
        }

        BindingRecord record = existing == null
                ? new BindingRecord(platformKey, userKey, kind, resolved.name())
                : existing;
        record.setKind(kind);
        record.setGameName(resolved.name());
        record.setFloodgate(resolved.floodgate());
        record.setJavaUuid(resolved.uuid() == null ? "" : resolved.uuid().toString());
        // 离线模式的 Java 版 UUID 就是按名字推导的离线 UUID：写进记录，接口返回值可直接用来核对白名单
        if (BindingRecord.KIND_JAVA.equals(kind) && !platformAdapter.isOnlineMode()) {
            record.setJavaUuid(WhitelistFile.offlineUuid(resolved.name()).toString());
        }

        boolean whitelistAdded = false;
        if (config.isBindingApplyToWhitelist()) {
            WhitelistOutcome outcome = applyWhitelistAdd(resolved.name(), kind, resolved.uuid());
            if (outcome == WhitelistOutcome.FAILED) {
                return Result.rejected(Rejection.WHITELIST_FAILED, resolved.name());
            }
            whitelistAdded = outcome == WhitelistOutcome.ADDED_BY_US;
        }
        // 白名单里本来就有该名字且 UUID 正确时（管理员手工添加），保持原有「非本绑定写入」的归属，
        // 解绑时不动它
        if (existing == null || !existing.isWhitelistAdded()) {
            record.setWhitelistAdded(whitelistAdded);
        }

        store.put(record);
        store.save();
        logger.info("已绑定 " + record.subjectKey() + " [" + kind + "] -> " + resolved.name()
                + (whitelistAdded ? "，已加入白名单" : ""));
        return Result.ok(record, true);
    }

    /**
     * 解除某一类绑定（只移除该类目下「由绑定写入」的白名单条目）。
     *
     * @param kind {@link BindingRecord#KIND_JAVA} 或 {@link BindingRecord#KIND_GEYSER}
     */
    public Result unbind(String platform, String userId, String kind) {
        operationLock.lock();
        try {
            return unbindKindLocked(platform, userId, kind);
        } finally {
            operationLock.unlock();
        }
    }

    private Result unbindKindLocked(String platform, String userId, String kind) {
        if (!isEnabled()) {
            return Result.rejected(Rejection.FEATURE_DISABLED, null);
        }
        BindingRecord record = store.get(platform, userId, kind);
        if (record == null) {
            return Result.rejected(Rejection.NOT_BOUND, null);
        }
        boolean whitelistRemoved = releaseWhitelistEntry(record);
        store.remove(platform, userId, kind);
        store.save();
        logger.info("已解绑 " + record.subjectKey() + " [" + record.getKind() + "]"
                + "（原游戏 ID: " + record.getGameName() + "）"
                + (whitelistRemoved ? "，已从白名单移除" : "，白名单条目保留"));
        return Result.removed(List.of(record), whitelistRemoved);
    }

    /**
     * 解除该账号的**全部**绑定（Java 与基岩都清），逐条回收白名单。
     *
     * <p>某条白名单移除失败不影响其余条目：已成功删除的照常落盘，失败的那条保留并如实回报。</p>
     */
    public Result unbindAll(String platform, String userId) {
        operationLock.lock();
        try {
            if (!isEnabled()) {
                return Result.rejected(Rejection.FEATURE_DISABLED, null);
            }
            List<BindingRecord> removed = new ArrayList<>(2);
            boolean anyWhitelistRemoved = false;
            for (String kind : ALL_KINDS) {
                BindingRecord record = store.get(platform, userId, kind);
                if (record == null) {
                    continue;
                }
                boolean whitelistRemoved = releaseWhitelistEntry(record);
                anyWhitelistRemoved |= whitelistRemoved;
                if (record.isWhitelistAdded() && !whitelistRemoved) {
                    // 白名单没删掉就不要丢记录，否则以后再也回收不了这条条目
                    logger.warning("解绑时白名单移除失败，保留记录以便重试: " + record.getGameName());
                    continue;
                }
                store.remove(platform, userId, kind);
                removed.add(record);
            }
            if (removed.isEmpty()) {
                return Result.rejected(Rejection.NOT_BOUND, null);
            }
            store.save();
            logger.info("已清空 " + BindingRecord.bindingKey(platform, userId, "") + " 的 "
                    + removed.size() + " 条绑定");
            return Result.removed(removed, anyWhitelistRemoved);
        } finally {
            operationLock.unlock();
        }
    }

    /** 取该账号的某一条绑定（kind 为 null 时返回 Java 那条，保证旧客户端语义）。 */
    public BindingRecord lookup(String platform, String userId, String kind) {
        if (kind == null || kind.isBlank()) {
            return store.get(platform, userId, BindingRecord.KIND_JAVA);
        }
        return store.get(platform, userId, kind);
    }

    /** 取该账号的全部绑定（0~2 条，顺序为 Java 在前）。 */
    public List<BindingRecord> lookupAll(String platform, String userId) {
        return store.getAll(platform, userId);
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

    private WhitelistOutcome applyWhitelistAdd(String name, String kind, UUID knownUuid) {
        if (BindingRecord.isGeyser(kind)) {
            return applyFloodgateWhitelistAdd(name, knownUuid);
        }
        if (platformAdapter.isOnlineMode()) {
            return applyWhitelistCommand(name);
        }
        return applyOfflineWhitelistAdd(name);
    }

    /**
     * 正版验证开启时的路径：{@code whitelist add <名字>} 得到的正是玩家登录用的 UUID，保持原有机制。
     *
     * <p>该指令对已在白名单里的名字是空操作（服务端不改写已有条目的 UUID，只回一句 already whitelisted），
     * 所以写入前先看文件里有没有同名条目：有则说明它不是本次绑定写入，据此交出 whitelistAdded 归属，
     * 避免解绑时误删管理员手工添加的条目。</p>
     */
    private WhitelistOutcome applyWhitelistCommand(String name) {
        boolean existed = false;
        Path file = platformAdapter.getWhitelistFile();
        if (file != null) {
            try {
                existed = WhitelistFile.findUuid(file, name) != null;
            } catch (IOException e) {
                logger.warning("读取白名单文件失败，无法判定条目归属: " + name + "（" + e.getMessage() + "）");
            }
        }
        if (!platformAdapter.executeCommand("whitelist add " + name)) {
            logger.warning("加入白名单失败: " + name + "（请确认服务器已开启 white-list，且指令未被其它插件拦截）");
            return WhitelistOutcome.FAILED;
        }
        return existed ? WhitelistOutcome.ALREADY_PRESENT : WhitelistOutcome.ADDED_BY_US;
    }

    /**
     * 离线模式 + Java 版：不依赖 {@code /whitelist add <名字>}，直接读改写白名单文件。
     *
     * <p>服务端解析名字时会查 Mojang 名字 API，把正版 UUID 写进白名单，与离线登录用的 UUID 不符。</p>
     */
    private WhitelistOutcome applyOfflineWhitelistAdd(String name) {
        Path file = platformAdapter.getWhitelistFile();
        if (file == null) {
            logger.warning("当前平台不支持读写白名单文件，离线模式下无法写入正确的离线 UUID: " + name);
            return WhitelistOutcome.FAILED;
        }

        UUID uuid = WhitelistFile.offlineUuid(name);
        try {
            WhitelistFile.Change change = WhitelistFile.upsert(file, name, uuid);
            if (!reloadWhitelist()) {
                return WhitelistOutcome.FAILED;
            }
            // 重载后重新读回校验：服务端内存名单（下次保存时的来源）必须就是这条离线 UUID
            UUID written = WhitelistFile.findUuid(file, name);
            if (!uuid.equals(written)) {
                logger.warning("白名单写入校验失败: " + name + " 期望 " + uuid + "，实际 " + written);
                return WhitelistOutcome.FAILED;
            }
            if (change == WhitelistFile.Change.PRESENT) {
                return WhitelistOutcome.ALREADY_PRESENT;
            }
            if (change == WhitelistFile.Change.REPLACED) {
                logger.info("已修正白名单中 " + name + " 的 UUID（原条目与离线登录不符）");
            }
            return WhitelistOutcome.ADDED_BY_US;
        } catch (IOException e) {
            logger.warning("写入白名单文件失败: " + file + "（" + e.getMessage() + "）");
            return WhitelistOutcome.FAILED;
        }
    }

    /**
     * 基岩版（Geyser + Floodgate）的白名单写入，**写后必回读校验**。
     *
     * <p>本版 Floodgate 的 {@code fwhitelist} 不接受 UUID 参数，只能用不带 Geyser 前缀的用户名；
     * 而它对名字的解析依赖 Floodgate 自己的 XUID 缓存 / Xbox Live API——缓存里没有该名字时，
     * 它只在控制台留一行 {@code Unable to find user in our cache}，**不抛异常、不改白名单**。
     * 而 `fwhitelist` 走的是 Cloud 指令框架，{@code executeCommand} 恒返回 true，
     * 早期版本因此会误报「已加入白名单」，实际白名单里还是旧的错误条目（离线 UUID），
     * 基岩玩家依旧进不来。所以这里不看指令返回值，只认白名单文件里实际写成了什么。
     *
     * @param name      解析得到的游戏 ID（带 Geyser 前缀，形如 {@code .ikarMing}）
     * @param knownUuid 玩家在线时拿到的真实 UUID，可为 null；非 null 时按它直接写入并校验
     */
    private WhitelistOutcome applyFloodgateWhitelistAdd(String name, UUID knownUuid) {
        String bare = floodgateName(name);
        // 玩家在线：手里就有 Floodgate 依 XUID 生成的真实 UUID，直接按「服务器登录时看到的名字」
        // （带前缀形态）写进白名单文件，完全绕开「名字解析要查 Xbox Live」这条不可靠路径。
        if (knownUuid != null) {
            if (!WhitelistFile.isFloodgateUuid(knownUuid)) {
                logger.warning("基岩版绑定拿到的 UUID 不是 Floodgate 生成的，拒绝写入白名单: " + name);
                return WhitelistOutcome.FAILED;
            }
            return writeWhitelistEntry(name, knownUuid);
        }

        if (platformAdapter.isFloodgateAvailable()) {
            if (!platformAdapter.executeCommand("fwhitelist add " + bare)) {
                logger.warning("执行 fwhitelist add " + bare + " 失败（指令层拒绝）");
                return WhitelistOutcome.FAILED;
            }
        } else {
            logger.warning("未检测到 Floodgate 的 fwhitelist 指令，基岩版白名单退回 /whitelist add "
                    + bare + "（该路径下服务端可能写入与基岩玩家不匹配的 UUID，请确认 Floodgate 已装好）");
            if (!platformAdapter.executeCommand("whitelist add " + bare)) {
                return WhitelistOutcome.FAILED;
            }
        }
        // fwhitelist 按「不带前缀的名字」入册，所以条目名字是裸名；两种形态都查一遍以防版本差异
        return verifyFloodgateEntry(name, bare);
    }

    /** 按已知 UUID 直接写白名单文件并重载，复用离线路径的写后校验。 */
    private WhitelistOutcome writeWhitelistEntry(String name, UUID uuid) {
        Path file = platformAdapter.getWhitelistFile();
        if (file == null) {
            logger.warning("当前平台不支持读写白名单文件，无法写入基岩版白名单: " + name);
            return WhitelistOutcome.FAILED;
        }
        try {
            WhitelistFile.Change change = WhitelistFile.upsert(file, name, uuid);
            if (!reloadWhitelist()) {
                return WhitelistOutcome.FAILED;
            }
            UUID written = WhitelistFile.findUuid(file, name);
            if (!uuid.equals(written)) {
                logger.warning("白名单写入校验失败: " + name + " 期望 " + uuid + "，实际 " + written);
                return WhitelistOutcome.FAILED;
            }
            if (change == WhitelistFile.Change.PRESENT) {
                return WhitelistOutcome.ALREADY_PRESENT;
            }
            if (change == WhitelistFile.Change.REPLACED) {
                logger.info("已修正白名单中 " + name + " 的 UUID（原条目与基岩版身份不符）");
            }
            return WhitelistOutcome.ADDED_BY_US;
        } catch (IOException e) {
            logger.warning("写入白名单文件失败: " + file + "（" + e.getMessage() + "）");
            return WhitelistOutcome.FAILED;
        }
    }

    /**
     * 回读白名单，确认 {@code fwhitelist} 真的写进了「带 Floodgate UUID 的条目」。
     *
     * <p>三种情况会直接判失败并给出可执行的提示，而不是留下一条永远进不来的错误条目：
     * 指令没写进去（名字解析失败/缓存过期）、写进去的 UUID 不是 Floodgate 生成的、
     * 白名单文件不可读。
     *
     * @param names 候选条目名（带前缀与不带前缀两种形态，按顺序查）
     */
    private WhitelistOutcome verifyFloodgateEntry(String... names) {
        Path file = platformAdapter.getWhitelistFile();
        if (file == null) {
            logger.warning("当前平台不支持读白名单文件，无法校验基岩版白名单是否写入成功: " + String.join("/", names));
            return WhitelistOutcome.FAILED;
        }
        UUID written = null;
        try {
            for (String candidate : names) {
                written = WhitelistFile.findUuid(file, candidate);
                if (written != null) {
                    break;
                }
            }
        } catch (IOException e) {
            logger.warning("读取白名单文件失败: " + file + "（" + e.getMessage() + "）");
            return WhitelistOutcome.FAILED;
        }
        if (written == null) {
            logger.warning("fwhitelist 没有把 " + String.join("/", names)
                    + " 写进白名单：Floodgate 没能把该名字解析成 XUID（Xbox Live 缓存里没有它）。"
                    + "请让该玩家先用基岩版登录一次，或在玩家在线时重新绑定。");
            return WhitelistOutcome.FAILED;
        }
        if (!WhitelistFile.isFloodgateUuid(written)) {
            logger.warning("白名单中 " + String.join("/", names) + " 的 UUID 不是 Floodgate 生成的（实际 "
                    + written + "），基岩玩家仍会被拒绝。请删除该条目后，在该玩家在线时重新绑定。");
            return WhitelistOutcome.FAILED;
        }
        return WhitelistOutcome.ADDED_BY_US;
    }

    private boolean reloadWhitelist() {
        if (platformAdapter.reloadWhitelist()) {
            return true;
        }
        logger.warning("白名单重载失败，服务端内存名单仍是旧的，且下次保存会覆盖刚写入的文件");
        return false;
    }

    /** Floodgate 的 fwhitelist 用不带 Geyser 前缀的玩家名 */
    private String floodgateName(String name) {
        String prefix = config.getGeyserPrefix();
        if (prefix == null || prefix.isEmpty() || !name.startsWith(prefix)) {
            return name;
        }
        String stripped = name.substring(prefix.length());
        return stripped.isEmpty() ? name : stripped;
    }

    /**
     * 仅在「该条目由绑定写入」时移除白名单，避免误删管理员手工添加的同名条目。
     *
     * <p>离线模式下不能再用 {@code whitelist remove <名字>}：它同样会把名字解析成别的 UUID。</p>
     */
    private boolean releaseWhitelistEntry(BindingRecord record) {
        if (record == null || !record.isWhitelistAdded()) {
            return false;
        }
        if (record.isGeyser()) {
            return releaseFloodgateWhitelistEntry(record.getGameName());
        }
        if (platformAdapter.isOnlineMode()) {
            return removeByCommand("whitelist remove", record.getGameName());
        }
        return releaseOfflineWhitelistEntry(record.getGameName());
    }

    private boolean releaseFloodgateWhitelistEntry(String name) {
        if (platformAdapter.isFloodgateAvailable()) {
            return removeByCommand("fwhitelist remove", floodgateName(name));
        }
        logger.warning("未检测到 Floodgate 的 fwhitelist 指令，退回 /whitelist remove "
                + floodgateName(name));
        return removeByCommand("whitelist remove", floodgateName(name));
    }

    private boolean removeByCommand(String command, String name) {
        boolean success = platformAdapter.executeCommand(command + " " + name);
        if (!success) {
            logger.warning("从白名单移除失败: " + name);
        }
        return success;
    }

    private boolean releaseOfflineWhitelistEntry(String name) {
        Path file = platformAdapter.getWhitelistFile();
        if (file == null) {
            logger.warning("当前平台不支持读写白名单文件，无法按 UUID 移除离线模式白名单条目: " + name);
            return false;
        }
        try {
            if (!WhitelistFile.remove(file, name, WhitelistFile.offlineUuid(name))) {
                // 文件里已经没有这条了：视作已回收，否则记录会永远留在 bindings.json 里回收不掉
                logger.info("白名单中已无 " + name + " 的条目，无需移除");
                return true;
            }
            return reloadWhitelist();
        } catch (IOException e) {
            logger.warning("移除白名单文件条目失败: " + name + "（" + e.getMessage() + "）");
            return false;
        }
    }

    // ===== 结果类型 =====

    /**
     * 白名单写入结果。
     *
     * <p>必须区分「失败」与「本来就有正确条目」：后者（管理员手工加过白名单）绑定应当照常成功，
     * 只是该条目不算本次绑定写入，解绑时不能删。</p>
     */
    private enum WhitelistOutcome {
        ADDED_BY_US,
        ALREADY_PRESENT,
        FAILED
    }

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
        private final List<BindingRecord> removedRecords;

        private Result(boolean success, Rejection rejection, BindingRecord record,
                       boolean whitelistChanged, String gameName, List<BindingRecord> removedRecords) {
            this.success = success;
            this.rejection = rejection;
            this.record = record;
            this.whitelistChanged = whitelistChanged;
            this.gameName = gameName;
            this.removedRecords = removedRecords == null ? List.of() : removedRecords;
        }

        static Result ok(BindingRecord record, boolean whitelistChanged) {
            return new Result(true, null, record, whitelistChanged, record.getGameName(), List.of());
        }

        /** 解绑结果：可能一次移除多条（Java + 基岩）。 */
        static Result removed(List<BindingRecord> records, boolean whitelistChanged) {
            BindingRecord first = records.isEmpty() ? null : records.get(0);
            String name = first == null ? null : first.getGameName();
            return new Result(true, null, first, whitelistChanged, name, List.copyOf(records));
        }

        static Result rejected(Rejection rejection, String gameName) {
            return new Result(false, rejection, null, false, gameName, List.of());
        }

        public boolean isSuccess() {
            return success;
        }

        public Rejection getRejection() {
            return rejection;
        }

        /** 主要记录（bind 时为新建/更新的那条；解绑时为首条被移除的记录，可能为 null） */
        public BindingRecord getRecord() {
            return record;
        }

        /** 被移除的全部记录（bind 时为空列表） */
        public List<BindingRecord> getRemovedRecords() {
            return removedRecords;
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
