package io.github.pereirrd.awsjavacache.cacheaside;

import static io.github.pereirrd.awsjavacache.cacheaside.constants.CacheAsideErrorMessages.CACHE_ID_NOT_FOUND;
import static io.github.pereirrd.awsjavacache.cacheaside.constants.CacheAsideErrorMessages.CACHE_KEY_TEMPLATE_REQUIRES_ID;
import static io.github.pereirrd.awsjavacache.cacheaside.constants.CacheAsideErrorMessages.CACHE_REGION_OR_KEY_REQUIRED;
import static io.github.pereirrd.awsjavacache.cacheaside.constants.CacheAsideErrorMessages.CACHE_TTL_NOT_FOUND;
import static io.github.pereirrd.awsjavacache.cacheaside.constants.CacheAsideErrorMessages.ENTITY_CLASS_REQUIRED;

import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheId;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheKey;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheName;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheRegion;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheTtl;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Reads cache-aside annotations from an entity class and builds key/TTL metadata via reflection.
 *
 * <p>No bytecode weaving or AOP: the application calls {@link #resolve(Class)} explicitly.
 */
public final class CacheAsideAnnotationResolver {

    private static final String ID_PLACEHOLDER = "{id}";
    private static final String JPA_ID_ANNOTATION = "jakarta.persistence.Id";

    private CacheAsideAnnotationResolver() {}

    public static <M> CacheAsideMetadata<?> resolve(Class<M> entityClass) {
        Objects.requireNonNull(entityClass, ENTITY_CLASS_REQUIRED);
        var idAccessor = findIdAccessor(entityClass);
        var keyTemplate = resolveKeyTemplate(entityClass);
        var entryTtl = resolveEntryTtl(entityClass, idAccessor);
        var cacheKeyPrefix = extractPrefix(keyTemplate);
        var cacheKeyForId = buildCacheKeyFunction(keyTemplate);
        var idExtractor = buildIdExtractor(idAccessor);
        return new CacheAsideMetadata<>(cacheKeyPrefix, cacheKeyForId, entryTtl, idExtractor);
    }

    private static String resolveKeyTemplate(Class<?> entityClass) {
        var classKey = entityClass.getAnnotation(CacheKey.class);
        if (classKey != null && !classKey.value().isBlank()) {
            var template = classKey.value().trim();
            if (!template.contains(ID_PLACEHOLDER)) {
                throw new IllegalArgumentException(CACHE_KEY_TEMPLATE_REQUIRES_ID);
            }
            return template;
        }

        var region = resolveRegion(entityClass);
        if (region.isPresent()) {
            return region.orElseThrow() + ":" + ID_PLACEHOLDER;
        }

        throw new IllegalArgumentException(CACHE_REGION_OR_KEY_REQUIRED);
    }

    private static Optional<String> resolveRegion(Class<?> entityClass) {
        var cacheRegion = entityClass.getAnnotation(CacheRegion.class);
        if (cacheRegion != null && !cacheRegion.value().isBlank()) {
            return Optional.of(cacheRegion.value().trim());
        }
        var cacheName = entityClass.getAnnotation(CacheName.class);
        if (cacheName != null && !cacheName.value().isBlank()) {
            return Optional.of(cacheName.value().trim());
        }
        return Optional.empty();
    }

    private static String extractPrefix(String keyTemplate) {
        var placeholderIndex = keyTemplate.indexOf(ID_PLACEHOLDER);
        if (placeholderIndex <= 0) {
            return "";
        }
        return keyTemplate.substring(0, placeholderIndex);
    }

    private static <ID> Function<ID, String> buildCacheKeyFunction(String keyTemplate) {
        return id -> keyTemplate.replace(ID_PLACEHOLDER, Objects.toString(id, ""));
    }

    private static Duration resolveEntryTtl(Class<?> entityClass, IdAccessor idAccessor) {
        if (idAccessor.field() != null) {
            var fieldTtl = idAccessor.field().getAnnotation(CacheTtl.class);
            if (fieldTtl != null) {
                return toDuration(fieldTtl);
            }
        }
        var classTtl = entityClass.getAnnotation(CacheTtl.class);
        if (classTtl != null) {
            return toDuration(classTtl);
        }
        throw new IllegalArgumentException(CACHE_TTL_NOT_FOUND);
    }

    private static Duration toDuration(CacheTtl cacheTtl) {
        return Duration.of(cacheTtl.value(), cacheTtl.unit().toChronoUnit());
    }

    private static IdAccessor findIdAccessor(Class<?> entityClass) {
        var fromField = streamFields(entityClass)
                .flatMap(field -> resolveIdAccessor(entityClass, field).stream())
                .findFirst();
        if (fromField.isPresent()) {
            return fromField.orElseThrow();
        }
        return streamMethods(entityClass)
                .flatMap(method -> resolveIdAccessor(method).stream())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(CACHE_ID_NOT_FOUND));
    }

    private static Optional<IdAccessor> resolveIdAccessor(Method method) {
        if (!method.isAnnotationPresent(CacheId.class) && !method.isAnnotationPresent(CacheKey.class)) {
            return Optional.empty();
        }
        if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) {
            return Optional.empty();
        }
        method.setAccessible(true);
        return Optional.of(new IdAccessor(null, method));
    }

    private static Optional<IdAccessor> resolveIdAccessor(Class<?> entityClass, Field field) {
        if (isIdField(field)) {
            return Optional.of(
                    new IdAccessor(field, findGetter(entityClass, field).orElse(null)));
        }
        if (field.isAnnotationPresent(CacheKey.class)) {
            return Optional.of(
                    new IdAccessor(field, findGetter(entityClass, field).orElse(null)));
        }
        return Optional.empty();
    }

    private static boolean isIdField(Field field) {
        return field.isAnnotationPresent(CacheId.class) || hasJpaId(field);
    }

    private static boolean hasJpaId(Field field) {
        return Arrays.stream(field.getAnnotations())
                .anyMatch(annotation ->
                        JPA_ID_ANNOTATION.equals(annotation.annotationType().getName()));
    }

    private static Optional<Method> findGetter(Class<?> entityClass, Field field) {
        var capitalized = capitalize(field.getName());
        var getterNames = Stream.of("get" + capitalized, "is" + capitalized, field.getName());
        return getterNames
                .map(name -> findMethod(entityClass, name))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<Method> findMethod(Class<?> entityClass, String name) {
        return Arrays.stream(entityClass.getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .findFirst();
    }

    private static Stream<Field> streamFields(Class<?> entityClass) {
        var fields = entityClass.getDeclaredFields();
        Arrays.stream(fields).forEach(field -> field.setAccessible(true));
        return Arrays.stream(fields).filter(field -> !Modifier.isStatic(field.getModifiers()));
    }

    private static Stream<Method> streamMethods(Class<?> entityClass) {
        return Arrays.stream(entityClass.getDeclaredMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()));
    }

    private static <ID> Function<Object, ID> buildIdExtractor(IdAccessor idAccessor) {
        return entity -> {
            Objects.requireNonNull(entity, "entity");
            try {
                if (idAccessor.getter() != null) {
                    return castId(idAccessor.getter().invoke(entity));
                }
                return castId(idAccessor.field().get(entity));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException(
                        "Unable to read cache id from " + entity.getClass().getName(), exception);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <ID> ID castId(Object id) {
        return (ID) id;
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record IdAccessor(Field field, Method getter) {}
}
