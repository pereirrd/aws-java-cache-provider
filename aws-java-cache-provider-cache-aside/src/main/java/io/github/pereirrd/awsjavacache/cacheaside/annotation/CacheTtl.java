package io.github.pereirrd.awsjavacache.cacheaside.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Entry TTL for cache-aside reads and explicit {@code putCached} writes.
 *
 * <p>When present on both type and field, the field annotation wins.
 */
@Documented
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheTtl {

    /** Duration magnitude. */
    long value() default 5;

    /** Unit for {@link #value()}. Defaults to minutes. */
    TimeUnit unit() default TimeUnit.MINUTES;
}
