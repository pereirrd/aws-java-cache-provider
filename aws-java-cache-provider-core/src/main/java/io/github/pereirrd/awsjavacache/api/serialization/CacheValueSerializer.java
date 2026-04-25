package io.github.pereirrd.awsjavacache.api.serialization;

import java.util.Objects;

/**
 * Converts domain values to cache payloads and back. Typically UTF-8 strings or binary-safe encodings.
 *
 * @param <M> cached model type
 */
public interface CacheValueSerializer<M> {

    String serialize(M value);

    M deserialize(String payload);

    /**
     * Identity serializer for {@link String} values stored as-is in the cache.
     */
    static CacheValueSerializer<String> utf8Strings() {
        return Utf8StringSerializer.INSTANCE;
    }

    enum Utf8StringSerializer implements CacheValueSerializer<String> {
        INSTANCE;

        @Override
        public String serialize(String value) {
            return Objects.requireNonNull(value, "value");
        }

        @Override
        public String deserialize(String payload) {
            return Objects.requireNonNull(payload, "payload");
        }
    }
}
