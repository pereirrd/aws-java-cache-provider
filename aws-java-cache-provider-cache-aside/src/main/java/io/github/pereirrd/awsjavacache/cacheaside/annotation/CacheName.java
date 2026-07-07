package io.github.pereirrd.awsjavacache.cacheaside.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Alias for {@link CacheRegion}. Prefer {@link CacheRegion} in new code; both are resolved identically.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheName {

    /** Cache name / namespace prefix (for example {@code "users"}). */
    String value();
}
