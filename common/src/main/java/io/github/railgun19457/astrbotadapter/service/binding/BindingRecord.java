package io.github.railgun19457.astrbotadapter.service.binding;

/**
 * 一条「外部账号 ↔ 游戏 ID」的绑定记录。
 *
 * <p>以 subject（平台 + 用户 ID）为主键；反向索引由 {@link BindingStore} 维护，
 * 保证一个游戏 ID 同时只被一个外部账号占用。</p>
 */
public final class BindingRecord {

    private String subjectPlatform = "";
    private String subjectUserId = "";
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

    public BindingRecord(String subjectPlatform, String subjectUserId, String gameName) {
        this.subjectPlatform = subjectPlatform == null ? "" : subjectPlatform;
        this.subjectUserId = subjectUserId == null ? "" : subjectUserId;
        this.gameName = gameName == null ? "" : gameName;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 绑定主键：平台名小写 + 用户 ID 原样（用户 ID 大小写敏感）
     */
    public static String subjectKey(String platform, String userId) {
        return (platform == null ? "" : platform.trim().toLowerCase())
                + ":" + (userId == null ? "" : userId.trim());
    }

    public String subjectKey() {
        return subjectKey(subjectPlatform, subjectUserId);
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
