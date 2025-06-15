package com.frever.ml.utils;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AsyncServiceUsingOwnThreadPool {
    protected static final int DEFAULT_CONCURRENCY = 15;
    protected ExecutorService executor;

    @PostConstruct
    protected void init() {
        int concurrency = getCurrency();
        int queueSize = getQueueSize();
        BlockingQueue<Runnable> workQueue =
            queueSize > 0 ? new ArrayBlockingQueue<>(queueSize) : new SynchronousQueue<>();
        executor = new ThreadPoolExecutor(
            concurrency,
            concurrency,
            0L,
            TimeUnit.MILLISECONDS,
            workQueue,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PreDestroy
    protected void destroy() {
        executor.shutdown();
        try {
            var stopped = executor.awaitTermination(10, TimeUnit.SECONDS);
            if (!stopped) {
                Log.warn("Executor service did not shutdown in 10 seconds.");
            }
        } catch (InterruptedException e) {
            Log.warn("Executor service shutdown was interrupted.", e);
        }
    }

    protected int getCurrency() {
        return DEFAULT_CONCURRENCY;
    }

    protected int getQueueSize() {
        return getCurrency();
    }
}
