package com.rednote.rednoteauth;

import com.rednote.rednoteauth.config.ThreadPoolConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 只加载线程池配置，不连接 Redis、数据库或短信服务。 */
class ThreadPoolTaskExecutorTests {

    @Test
    void executesOnWorkerThreadWithoutBlockingCallerAndShutsDown() throws Exception {
        ThreadPoolTaskExecutor executor;
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        try (var context = new AnnotationConfigApplicationContext(ThreadPoolConfig.class)) {
            executor = context.getBean("smsTaskExecutor", ThreadPoolTaskExecutor.class);
            try {
                executor.execute(() -> {
                    worker.set(Thread.currentThread());
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
                assertTrue(started.await(3, TimeUnit.SECONDS));
                assertNotSame(Thread.currentThread(), worker.get());
                assertTrue(worker.get().getName().startsWith("sms-send-"));
                assertEquals(1, finished.getCount(), "调用线程应在后台任务完成前继续执行");
            } finally {
                release.countDown();
            }
            assertTrue(finished.await(3, TimeUnit.SECONDS));
        }
        assertTrue(executor.getThreadPoolExecutor().isTerminated());
    }

    @Test
    void fullPoolRejectsTaskInsteadOfRunningOnCallerThread() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch workersStarted = new CountDownLatch(8);
        try (var context = new AnnotationConfigApplicationContext(ThreadPoolConfig.class)) {
            var executor = context.getBean("smsTaskExecutor", ThreadPoolTaskExecutor.class);
            Runnable blockingTask = () -> {
                workersStarted.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            };
            try {
                // 4 个核心线程 + 100 个排队任务 + 4 个扩展线程。
                for (int i = 0; i < 108; i++) {
                    executor.execute(blockingTask);
                }
                assertTrue(workersStarted.await(3, TimeUnit.SECONDS));
                assertThrows(TaskRejectedException.class,
                        () -> executor.execute(() -> fail("不应在调用线程执行被拒绝的任务")));
            } finally {
                release.countDown();
            }
        }
    }
}
