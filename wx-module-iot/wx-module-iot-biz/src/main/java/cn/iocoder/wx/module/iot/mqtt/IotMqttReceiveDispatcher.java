package cn.iocoder.wx.module.iot.mqtt;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 应用共享的有界接收池；动态连接不会各自创建数据库工作线程。 */
public final class IotMqttReceiveDispatcher implements Executor, AutoCloseable {
    private final ThreadPoolExecutor workers;

    public IotMqttReceiveDispatcher(int concurrency, int queueCapacity) {
        if (concurrency < 1 || concurrency > 32 || queueCapacity < 1 || queueCapacity > 4096) {
            throw new IllegalArgumentException("MQTT 接收线程或队列上限无效");
        }
        var sequence = new AtomicInteger();
        workers = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), task -> {
                    var thread = new Thread(task, "iot-mqtt-receive-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void execute(Runnable task) {
        // 饱和时拒绝，由会话关闭未确认接收；不能使用丢弃策略或虚报接收成功。
        workers.execute(task);
    }

    public boolean isShutdown() { return workers.isShutdown(); }

    @Override
    public void close() {
        // 取消排队 Future 会触发其完成清理，释放连接在途名额，避免关闭时阻塞网络回调。
        for (Runnable task : workers.shutdownNow()) {
            if (task instanceof Future<?> future) future.cancel(false);
        }
        try { workers.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
