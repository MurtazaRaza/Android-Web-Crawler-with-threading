package com.murti.here.webcrawler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Murti on 11-Apr-17.
 */

class RunnableManager {
    // Sets the amount of time an idle thread will wait for a task before
    // terminating
    private static final int KEEP_ALIVE_TIME = 1;

    // Sets the Time Unit to seconds
    private final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

    // Sets the initial threadpool size to 5
    private static final int CORE_POOL_SIZE = 5;

    // Sets the maximum threadpool size to 8
    private static final int MAXIMUM_POOL_SIZE = 8;

    // A queue of Runnables for crawling url
    private final BlockingQueue<Runnable> mCrawlingQueue;

    // A managed pool of background crawling threads
    private final ThreadPoolExecutor mCrawlingThreadPool;

    public RunnableManager() {
        mCrawlingQueue = new LinkedBlockingQueue<>();
        mCrawlingThreadPool = new ThreadPoolExecutor(CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT,
                mCrawlingQueue);
    }

    void addToCrawlingQueue(Runnable runnable) {
        mCrawlingThreadPool.execute(runnable);
    }

    void cancelAllRunnable() {
        mCrawlingThreadPool.shutdownNow();
    }

    int getUnusedPoolSize() {
        return MAXIMUM_POOL_SIZE - mCrawlingThreadPool.getActiveCount();
    }

    boolean isShuttingDown() {
        return mCrawlingThreadPool.isShutdown()
                || mCrawlingThreadPool.isTerminating();
    }
}
