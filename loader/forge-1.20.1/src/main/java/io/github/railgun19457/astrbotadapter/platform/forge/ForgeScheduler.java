package io.github.railgun19457.astrbotadapter.platform.forge;

import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forge 平台调度器
 * 主线程任务基于 MinecraftServer.execute；定时/异步任务基于 daemon 线程池。
 */
public class ForgeScheduler implements CommonScheduler {

    private static final long TICK_MILLIS = 50L;

    private final MinecraftServer server;
    private final ScheduledExecutorService executor;
    private final Map<Integer, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger taskIdCounter = new AtomicInteger(1);

    public ForgeScheduler(MinecraftServer server) {
        this.server = server;
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "AstrbotAdapter-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void runSync(Runnable task) {
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }

    @Override
    public void runAsync(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        executor.schedule(() -> runSync(task), delayTicks * TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void runLaterAsync(Runnable task, long delayTicks) {
        executor.schedule(task, delayTicks * TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public int runTimer(Runnable task, long delayTicks, long periodTicks) {
        int id = taskIdCounter.getAndIncrement();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                () -> runSync(task),
                delayTicks * TICK_MILLIS,
                periodTicks * TICK_MILLIS,
                TimeUnit.MILLISECONDS);
        tasks.put(id, future);
        return id;
    }

    @Override
    public int runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        int id = taskIdCounter.getAndIncrement();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                task,
                delayTicks * TICK_MILLIS,
                periodTicks * TICK_MILLIS,
                TimeUnit.MILLISECONDS);
        tasks.put(id, future);
        return id;
    }

    @Override
    public void cancelTask(int taskId) {
        ScheduledFuture<?> future = tasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void cancelAll() {
        tasks.values().forEach(future -> future.cancel(false));
        tasks.clear();
        executor.shutdownNow();
    }

    @Override
    public boolean isMainThread() {
        return server.isSameThread();
    }
}
