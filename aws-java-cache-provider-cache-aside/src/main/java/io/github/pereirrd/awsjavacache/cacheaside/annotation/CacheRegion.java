package io.github.pereirrd.awsjavacache.cacheaside.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cache namespace for an entity class. Combined with the identifier, produces keys such as {@code users:42}
 * when no explicit {@link CacheKey} template is present on the class.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheRegion {

    /** Region prefix (for example {@code "users"}). */
    String value();
}
