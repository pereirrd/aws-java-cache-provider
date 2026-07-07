package io.github.pereirrd.awsjavacache.writebehind;

/**
 * Optional hooks for queue depth, flush batches, origin failures, and backpressure rejections.
 * Implementations should be thread-safe when used from the processor thread and caller threads.
 */
public interface WriteBehindMetrics {

    WriteBehindMetrics NO_OP = new WriteBehindMetrics() {};

    default void onEnqueued(WriteBehindOperationType operationType) {}

    default void onBatchFlushed(int operationCount) {}

    default void onOriginFailure(WriteBehindOperationType operationType, RuntimeException cause) {}

    default void onBackpressureRejected(WriteBehindOperationType operationType) {}
}
