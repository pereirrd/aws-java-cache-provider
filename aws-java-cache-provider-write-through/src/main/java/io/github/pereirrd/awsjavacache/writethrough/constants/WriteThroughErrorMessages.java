package io.github.pereirrd.awsjavacache.writethrough.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WriteThroughErrorMessages {

    public static final String ENTITY_REQUIRED = "Entity must not be null";
    public static final String ID_FROM_ENTITY_REQUIRED = "Identifier extracted from entity must not be null";
    public static final String CACHE_UPDATE_AFTER_ORIGIN_SAVE_FAILED =
            "Entity persisted in backing store but cache update failed; cache may be stale";
    public static final String CACHE_INVALIDATE_AFTER_ORIGIN_DELETE_FAILED =
            "Entity deleted from backing store but cache invalidation failed; cache may be stale";
}
