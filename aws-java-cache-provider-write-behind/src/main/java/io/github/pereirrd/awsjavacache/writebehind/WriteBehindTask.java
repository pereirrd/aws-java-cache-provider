package io.github.pereirrd.awsjavacache.writebehind;

/**
 * Pending origin mutation drained from the write-behind queue. Ordering is preserved per enqueue order (FIFO).
 *
 * <p>Consumers should implement {@link io.github.pereirrd.awsjavacache.api.repository.BackingRepository#save(Object)}
 * and {@link io.github.pereirrd.awsjavacache.api.repository.BackingRepository#deleteById(Object)} idempotently so
 * retries or duplicate drains do not corrupt the backing store.
 */
public sealed interface WriteBehindTask<ID, M> permits WriteBehindTask.SaveTask, WriteBehindTask.DeleteTask {

    ID id();

    WriteBehindOperationType operationType();

    record SaveTask<ID, M>(ID id, M entity) implements WriteBehindTask<ID, M> {

        @Override
        public WriteBehindOperationType operationType() {
            return WriteBehindOperationType.SAVE;
        }
    }

    record DeleteTask<ID, M>(ID id) implements WriteBehindTask<ID, M> {

        @Override
        public WriteBehindOperationType operationType() {
            return WriteBehindOperationType.DELETE;
        }
    }
}
