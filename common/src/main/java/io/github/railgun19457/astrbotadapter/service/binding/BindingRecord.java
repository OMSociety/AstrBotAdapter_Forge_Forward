package io.github.railgun19457.astrbotadapter.service.binding;

/**
 * 一条「外部账号 ↔ 游戏 ID」的绑定记录。
 *
 * <p>主键是「平台 + 用户 ID + 绑定类型」：同一个外部账号**可以同时持有一条 Java 版绑定与
 * 一条基岩版绑定**（Floodgate 玩家通常两边各有名字），二者互不覆盖，因此服务器白名单里
 * 会同时存在两个条目。反向索引由 {@link BindingStore} 维护，保证一个游戏 ID 只被一个账号占用。</p>
 */
public final class BindingRecord {

    /** 绑定类型：Java 版玩家名 */
    public static final String KIND_JAVA = "java";
    /** 绑定类型：基岩版玩家名（Geyser + Floodgate） */
    public static final String KIND_GEYSER = "geyser";

    private String subjectPlatform = "";
    private String subjectUserId = "";
    /**
     * 绑定类型，取 {@link #KIND_JAVA} 或 {@link #KIND_GEYSER}。
     * 老版本数据没有该字段，载入时由 {@link #normalizeKind()} 按 floodgate 推断补齐。
     */
    private String kind = KIND_JAVA;
    private String gameName = "";
    private String javaUuid = "";
    private boolean floodgate = false;
    /**
     * 该白名单条目是否由本次绑定写入。
     * 解绑时据此决定是否移除——若白名单里本来就有同名条目（管理员手工添加），不能代管删除。
     */
    private boolean whitelistAdded = false;
    private long createdAt = 0L;
    private long updatedAt = 0L;

    public BindingRecord() {
    }

    /** 兼容旧调用：默认按 Java 版绑定处理。 */
    public BindingRecord(String subjectPlatform, String subjectUserId, String gameName) {
        this(subjectPlatform, subjectUserId, KIND_JAVA, gameName);
    }

    public BindingRecord(String subjectPlatform, String subjectUserId, String kind, String gameName) {
        this.subjectPlatform = subjectPlatform == null ? "" : subjectPlatform;
        this.subjectUserId = subjectUserId == null ? "" : subjectUserId;
        this.kind = normalizeKindValue(kind);
        this.gameName = gameName == null ? "" : gameName;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 由「是否基岩版」推导绑定类型。 */
    public static String kindOf(boolean bedrock) {
        return bedrock ? KIND_GEYSER : KIND_JAVA;
    }

    public static boolean isGeyser(String kind) {
        return KIND_GEYSER.equalsIgnoreCase(kind == null ? "" : kind.trim());
    }

    private static String normalizeKindValue(String value) {
        if (value == null || value.isBlank()) {
            return KIND_JAVA;
        }
        String trimmed = value.trim();
        if (KIND_GEYSER.equalsIgnoreCase(trimmed)) {
            return KIND_GEYSER;
        }
        return KIND_JAVA;
    }

    /**
     * 记录主键：平台名小写 + 用户 ID 原样（用户 ID 大小写敏感）+ 绑定类型。
     */
    public static String bindingKey(String platform, String userId, String kind) {
        return (platform == null ? "" : platform.trim().toLowerCase())
                + ":" + (userId == null ? "" : userId.trim())
                + ":" + normalizeKindValue(kind);
    }

    public String bindingKey() {
        return bindingKey(subjectPlatform, subjectUserId, kind);
    }

    /** 该记录所属账号（不含类型），用于「同一账号的所有绑定」查询与隐私安全的日志输出。 */
    public String subjectKey() {
        return (subjectPlatform == null ? "" : subjectPlatform.trim().toLowerCase())
                + ":" + (subjectUserId == null ? "" : subjectUserId.trim());
    }

    /**
     * 老数据没有 kind 字段时按 floodgate 推断；有值时保持原样。
     * 由 {@link BindingStore} 在载入时调用。
     */
    public void normalizeKind() {
        if (kind == null || kind.isBlank()) {
            kind = floodgate ? KIND_GEYSER : KIND_JAVA;
        } else {
            kind = normalizeKindValue(kind);
        }
    }

    public String getKind() {
        return normalizeKindValue(kind);
    }

    public void setKind(String kind) {
        this.kind = normalizeKindValue(kind);
    }

    public boolean isGeyser() {
        return isGeyser(kind);
    }

    public String getSubjectPlatform() {
        return subjectPlatform;
    }

    public void setSubjectPlatform(String subjectPlatform) {
        this.subjectPlatform = subjectPlatform == null ? "" : subjectPlatform;
    }

    public String getSubjectUserId() {
        return subjectUserId;
    }

    public void setSubjectUserId(String subjectUserId) {
        this.subjectUserId = subjectUserId == null ? "" : subjectUserId;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName == null ? "" : gameName;
    }

    public String getJavaUuid() {
        return javaUuid;
    }

    public void setJavaUuid(String javaUuid) {
        this.javaUuid = javaUuid == null ? "" : javaUuid;
    }

    public boolean isFloodgate() {
        return floodgate;
    }

    public void setFloodgate(boolean floodgate) {
        this.floodgate = floodgate;
    }

    public boolean isWhitelistAdded() {
        return whitelistAdded;
    }

    public void setWhitelistAdded(boolean whitelistAdded) {
        this.whitelistAdded = whitelistAdded;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
