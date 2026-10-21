package cn.iocoder.wx.module.iot.mqtt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** 手动推进异步任务，不启动定时器，也不依赖睡眠碰运气。 */
final class IotMqttManualExecutor extends AbstractExecutorService {
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private boolean shutdown;

    void drain() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    @Override
    public void execute(Runnable task) {
        if (shutdown) {
            throw new RejectedExecutionException();
        }
        tasks.add(task);
    }

    @Override public void shutdown() { shutdown = true; }
    @Override public boolean isShutdown() { return shutdown; }
    @Override public boolean isTerminated() { return shutdown && tasks.isEmpty(); }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    @Override public List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> pending = new ArrayList<>(tasks);
        tasks.clear();
        return pending;
    }
}
