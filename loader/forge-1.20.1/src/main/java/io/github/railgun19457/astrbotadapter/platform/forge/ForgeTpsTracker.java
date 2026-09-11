package io.github.railgun19457.astrbotadapter.platform.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 平台 TPS/MSPT 统计器。
 *
 * <p>Forge（原版服务端）没有 Bukkit/Paper 的 {@code getTPS()} API，也没有 {@code /tps} 命令；
 * 这里通过订阅 {@link TickEvent.ServerTickEvent}（END 阶段）自统计主线程 tick 间隔，
 * 按 1 分钟 / 5 分钟 / 15 分钟滑动窗口计算平均 TPS 与 MSPT。
 * 事件总线 API 在 1.20.1~1.21 均稳定，无需任何前置 mod。</p>
 *
 * <p>线程安全：统计在主线程进行，结果写入 {@code volatile} 缓存；
 * REST（Netty 线程）读取时直接取缓存快照，不加锁。</p>
 */
public class ForgeTpsTracker {

    /** 环形缓冲长度：15 分钟 @ 20 TPS */
    private static final int WINDOW_TICKS = 20 * 60 * 15;

    private static final int TPS_1M_TICKS = 20 * 60;
    private static final int TPS_5M_TICKS = 20 * 60 * 5;
    private static final int TPS_15M_TICKS = 20 * 60 * 15;

    /** 每个 tick 的间隔（纳秒），环形缓冲 */
    private final long[] tickIntervals = new long[WINDOW_TICKS];
    private int index = 0;
    private int count = 0;
    private long lastNanos = 0L;

    /** TPS 缓存：[1m, 5m, 15m]；MSPT 缓存（毫秒） */
    private volatile double[] tpsCache = new double[]{20.0, 20.0, 20.0};
    private volatile double msptCache = 50.0;

    private boolean registered = false;

    /**
     * 注册到 Forge 事件总线（服务器启动时调用）
     */
    public void register() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }
    }

    /**
     * 从 Forge 事件总线注销（服务器停止时调用）
     */
    public void unregister() {
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.nanoTime();
        if (lastNanos != 0L) {
            long elapsed = now - lastNanos;
            // 防止极端大间隔（如服务器挂起后恢复）污染窗口
            if (elapsed > 0L && elapsed < 5_000_000_000L) { // < 5s
                tickIntervals[index] = elapsed;
                index = (index + 1) % WINDOW_TICKS;
                if (count < WINDOW_TICKS) {
                    count++;
                }
            }
        }
        lastNanos = now;
        updateMetrics();
    }

    /**
     * 获取 TPS 快照 [1m, 5m, 15m]
     */
    public double[] getTps() {
        return tpsCache.clone();
    }

    /**
     * 获取 MSPT 快照（毫秒）
     */
    public double getMspt() {
        return msptCache;
    }

    private void updateMetrics() {
        if (count == 0) {
            return;
        }
        double tps1m = calcTps(TPS_1M_TICKS);
        double tps5m = calcTps(TPS_5M_TICKS);
        double tps15m = calcTps(TPS_15M_TICKS);
        tpsCache = new double[]{round2(tps1m), round2(tps5m), round2(tps15m)};
        msptCache = round2(calcMspt(TPS_1M_TICKS));
    }

    /**
     * 计算最近 windowTicks 个 tick 的平均 TPS。
     * 服务器刚启动、tick 数不足窗口时按实际 tick 数计算。
     */
    private double calcTps(int windowTicks) {
        int n = Math.min(count, windowTicks);
        if (n <= 0) {
            return 20.0;
        }
        long sum = 0L;
        int start = (index - n + WINDOW_TICKS) % WINDOW_TICKS;
        for (int i = 0; i < n; i++) {
            sum += tickIntervals[(start + i) % WINDOW_TICKS];
        }
        double avgMillis = (double) sum / n / 1_000_000.0;
        if (avgMillis <= 0.0) {
            return 20.0;
        }
        return Math.min(20.0, 1000.0 / avgMillis);
    }

    /**
     * 计算最近 windowTicks 个 tick 的平均耗时（毫秒）。
     */
    private double calcMspt(int windowTicks) {
        int n = Math.min(count, windowTicks);
        if (n <= 0) {
            return 50.0;
        }
        long sum = 0L;
        int start = (index - n + WINDOW_TICKS) % WINDOW_TICKS;
        for (int i = 0; i < n; i++) {
            sum += tickIntervals[(start + i) % WINDOW_TICKS];
        }
        return (double) sum / n / 1_000_000.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
