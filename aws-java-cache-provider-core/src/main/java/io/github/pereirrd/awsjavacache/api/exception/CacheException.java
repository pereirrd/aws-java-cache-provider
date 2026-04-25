package io.github.pereirrd.awsjavacache.api.exception;

/** Unchecked failure interacting with the cache layer. */
public class CacheException extends RuntimeException {

    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
