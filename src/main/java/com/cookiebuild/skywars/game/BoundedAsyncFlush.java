package com.cookiebuild.skywars.game;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Runs lifecycle persistence independently of Bukkit and bounds shutdown waiting. */
final class BoundedAsyncFlush {
    private BoundedAsyncFlush() {
    }

    static boolean runAndAwait(Runnable task, Duration timeout) {
        if (task == null || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("A task and positive timeout are required");
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(task);
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}
