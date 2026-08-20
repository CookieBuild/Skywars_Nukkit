package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class BoundedAsyncFlushTest {
    @Test
    void disabledPluginDoesNotOwnTheFlushThread() {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> worker = new AtomicReference<>();
        assertTrue(BoundedAsyncFlush.runAndAwait(() -> worker.set(Thread.currentThread()), Duration.ofSeconds(1)));
        assertNotEquals(caller, worker.get());
    }

    @Test
    void schedulerRejectionOrStuckWriteCannotBlockCleanupPastBudget() {
        CountDownLatch release = new CountDownLatch(1);
        long started = System.nanoTime();
        try {
            assertFalse(BoundedAsyncFlush.runAndAwait(() -> {
                try {
                    release.await();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }, Duration.ofMillis(75)));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 500);
        } finally {
            release.countDown();
        }
    }
}
