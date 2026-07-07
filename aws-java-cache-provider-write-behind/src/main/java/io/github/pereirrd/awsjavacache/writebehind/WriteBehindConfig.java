package io.github.pereirrd.awsjavacache.writebehind;

import java.time.Duration;
import java.util.Objects;

/**
 * Runtime configuration for {@link WriteBehindService}: in-memory queue capacity, batch drain size, periodic flush
 * interval, and shutdown timeout.
 *
 * <p><strong>Durability:</strong> pending writes live only in the JVM heap queue. A process crash before
 * {@link WriteBehindService#flush()} or {@link WriteBehindService#close()} may lose unflushed operations even though
 * the cache was already updated.
 */
public record WriteBehindConfig(int queueCapacity, int batchSize, Duration flushInterval, Duration shutdownTimeout) {

    public WriteBehindConfig {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        flushInterval = Objects.requireNonNull(flushInterval, "flushInterval");
        shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        if (flushInterval.isNegative()) {
            throw new IllegalArgumentException("flushInterval must not be negative");
        }
        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("shutdownTimeout must be positive");
        }
    }

    public static WriteBehindConfig defaults() {
        return new WriteBehindConfig(10_000, 100, Duration.ofSeconds(1), Duration.ofSeconds(30));
    }
}
