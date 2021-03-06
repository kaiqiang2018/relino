package com.relino.core.support.thread;

import com.relino.core.exception.HandleException;
import com.relino.core.support.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 通过BlockingQueue实现生产者消费者模型，避免添加的速度过快
 *
 * @author kaiqiang.he
 */
public class QueueSizeLimitExecutor<T> implements Runnable {
    
    private static final Logger log = LoggerFactory.getLogger(QueueSizeLimitExecutor.class);
    
    private static final int DEFAULT_QUEUE_SIZE = 1000;

    private AtomicBoolean isStop = new AtomicBoolean(false);

    private final String name;
    private final int coreThread;
    private final int maxThread;
    private final int queueSize;

    private BlockingQueue<T> queue;
    private Processor<T> processor;
    private ThreadPoolExecutor workers;

    public QueueSizeLimitExecutor(String name, int coreThread, int maxThread, int queueSize, Processor<T> processor) {

        Utils.check(name, Utils::isEmpty, "name不能为空");
        Utils.check(coreThread, v -> v <= 0, "coreThread应大于0, [" + coreThread + "]");
        Utils.check(maxThread, v -> v <= 0 || maxThread < coreThread, "coreThread应大于0且大于等于maxThread, [" + coreThread + "," + maxThread + "]");
        Utils.check(queueSize, v -> v <= 0, "queueSize必须大于0");
        Utils.checkNoNull(processor);

        this.name = name;
        this.coreThread = coreThread;
        this.maxThread = maxThread;
        this.queueSize = queueSize;
        this.processor = processor;

        init();
    }

    public QueueSizeLimitExecutor(String name, int coreThread, int maxThread, Processor<T> processor) {
        this(name, coreThread, maxThread, DEFAULT_QUEUE_SIZE, processor);
    }

    private void init() {
        queue = new ArrayBlockingQueue<>(queueSize);

        // workQueue 的大小必须为1
        // 拒绝策略必须为 DiscardPolicy 直接丢弃
        workers = new ThreadPoolExecutor(
                coreThread,
                maxThread,
                1, TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(1),
                new NamedThreadFactory("job-execute-" + name + "-" , true),
                new ThreadPoolExecutor.DiscardPolicy());
    }

    public boolean addItem(T job) {

        if(isStop.get()) {
            throw new IllegalStateException("调用shutdown()之后不可以再添加任务");
        }

        boolean offered = queue.offer(job);
        if(offered) {
            workers.execute(this);
        }
        return offered;
    }

    public boolean addItem(T job, long timeout, TimeUnit unit) throws InterruptedException {

        if(isStop.get()) {
            throw new IllegalStateException("调用shutdown()之后不可以再添加任务");
        }

        boolean offered = queue.offer(job, timeout, unit);
        if(offered) {
            workers.execute(this);
        }
        return offered;
    }

    @Override
    public void run() {
        try {
            while (!queue.isEmpty()) {
                T elem = queue.poll();
                if(elem == null) {
                    return ;
                }

                processor.process(elem);
            }
        } catch (Throwable t) {
            HandleException.handleUnExpectedException(t);
        }
    }

    public void shutdown() {
        if(isStop.compareAndSet(false, true)) {
            workers.shutdown();
            try {
                // 等待10秒
                workers.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Shutdown workers interrupted.", e);
            }
        }
    }
}
