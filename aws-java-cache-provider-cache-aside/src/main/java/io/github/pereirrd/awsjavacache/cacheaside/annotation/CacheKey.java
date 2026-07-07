package io.github.pereirrd.awsjavacache.cacheaside.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares how cache keys are built for an entity.
 *
 * <p>On a class, {@link #value()} is a template with an {@code {id}} placeholder (for example
 * {@code "users:{id}"}). On a field or method, marks the identifier accessor used with {@code {id}}.
 */
@Documented
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheKey {

    /**
     * Key template when placed on a type (must contain {@code {id}}), or empty when placed on the id field or
     * getter.
     */
    String value() default "";
}
