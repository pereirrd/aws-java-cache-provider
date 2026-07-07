package io.github.pereirrd.awsjavacache.writebehind.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WriteBehindErrorMessages {

    public static final String ENTITY_REQUIRED = "Entity must not be null";
    public static final String ID_FROM_ENTITY_REQUIRED = "Identifier extracted from entity must not be null";
    public static final String CACHE_UPDATE_FAILED = "Failed to update cache before enqueueing origin write";
    public static final String CACHE_INVALIDATE_FAILED = "Failed to invalidate cache before enqueueing origin delete";
    public static final String QUEUE_FULL_BACKPRESSURE =
            "Write-behind queue is full; origin write rejected (cache already updated)";
    public static final String SERVICE_CLOSED = "Write-behind service is closed";
    public static final String FLUSH_INTERRUPTED = "Interrupted while flushing pending origin writes";
    public static final String SHUTDOWN_FLUSH_FAILED = "Failed to flush pending origin writes during shutdown";
}
