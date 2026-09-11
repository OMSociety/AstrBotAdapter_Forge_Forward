package io.github.railgun19457.astrbotadapter.platform;

import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 平台适配器接口
 * 定义不同服务器平台需要实现的统一接口
 */
public interface PlatformAdapter {

    // ===== 平台信息 =====

    /**
     * 获取平台类型
     */
    PlatformType getPlatformType();

    /**
     * 获取服务器版本
     */
    String getServerVersion();

    /**
     * 获取服务器MOTD
     */
    String getServerMotd();

    /**
     * 获取服务器运行时间（毫秒）
     */
    long getServerUptime();

    /**
     * 获取服务器名称
     */
    String getServerName();

    /**
     * 获取服务器对象
     */
    CommonServer getServer();

    // ===== 玩家操作 =====

    /**
     * 获取所有在线玩家
     */
    Collection<CommonPlayer> getOnlinePlayers();

    /**
     * 通过名称获取玩家
     * @param name 玩家名称
     * @return 玩家对象（可能不存在）
     */
    Optional<CommonPlayer> getPlayer(String name);

    /**
     * 通过UUID获取玩家
     * @param uuid 玩家UUID
     * @return 玩家对象（可能不存在）
     */
    Optional<CommonPlayer> getPlayer(UUID uuid);

    /**
     * 获取在线玩家数量
     */
    int getOnlinePlayerCount();

    /**
     * 获取最大玩家数量
     */
    int getMaxPlayers();

    // ===== 消息发送 =====

    /**
     * 向所有玩家广播消息
     * @param message 消息内容
     */
    void broadcastMessage(String message);

    /**
     * 向指定玩家发送消息
     * @param player 玩家对象
     * @param message 消息内容
     */
    void sendMessage(CommonPlayer player, String message);

    /**
     * 在控制台输出消息
     * @param message 消息内容
     */
    void sendConsoleMessage(String message);

    // ===== 指令执行 =====

    /**
     * 以控制台身份执行指令
     * @param command 指令（不含/）
     * @return 是否执行成功
     */
    boolean executeCommand(String command);

    /**
     * 以玩家身份执行指令
     * @param player 玩家对象
     * @param command 指令（不含/）
     * @return 是否执行成功
     */
    boolean executeCommand(CommonPlayer player, String command);

    // ===== 白名单 =====

    /**
     * 服务器是否开启正版验证（online-mode）。
     *
     * <p>离线（online-mode=false）时按用户名执行 {@code whitelist add} 会拿到 Mojang 的正版 UUID，
     * 而玩家登录用的是本地推导的离线 UUID，两者不符 → 白名单永远匹配不上。此时绑定服务必须改走
     * {@link #getWhitelistFile()} 直接读写白名单文件。默认 true 表示走指令路径（与历史行为一致）。</p>
     */
    default boolean isOnlineMode() {
        return true;
    }

    /**
     * 白名单文件（{@code whitelist.json}）路径；平台不支持直接读写时返回 null。
     */
    default Path getWhitelistFile() {
        return null;
    }

    /**
     * 让服务端从磁盘重新载入白名单（等价于执行 {@code /whitelist reload}）。
     *
     * <p>直接改过白名单文件后必须调用：否则服务端内存里仍是旧名单（新玩家照样进不来），
     * 且内存名单下一次保存会把刚写入的文件覆盖回去。</p>
     *
     * @return 是否重载成功
     */
    default boolean reloadWhitelist() {
        return false;
    }

    /**
     * 是否可用 Floodgate 的 {@code fwhitelist} 指令。
     *
     * <p>基岩版 UUID 由 Bedrock XUID 生成，本地算不出来，只能交给 Floodgate 自己写白名单。</p>
     */
    default boolean isFloodgateAvailable() {
        return false;
    }

    // ===== 调度器 =====

    /**
     * 获取调度器
     */
    CommonScheduler getScheduler();

    // ===== 日志 =====

    /**
     * 获取最近的日志
     * @param lines 行数
     * @return 日志列表
     */
    List<String> getRecentLogs(int lines);

    /**
     * 获取指定时间范围的日志
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 日志列表
     */
    List<String> getLogsByTimeRange(long startTime, long endTime);

    // ===== 生命周期 =====

    /**
     * 初始化适配器
     */
    void initialize();

    /**
     * 关闭适配器
     */
    void shutdown();

    /**
     * 注册事件监听器
     */
    void registerListeners();

    /**
     * 注销事件监听器
     */
    void unregisterListeners();
}
