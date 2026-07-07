package io.github.pereirrd.awsjavacache.writebehind;

/** Point-in-time counters for the in-memory write-behind queue. */
public record WriteBehindQueueStats(
        int queuedCount, long flushedOperations, long failedOperations, long rejectedOperations) {}
