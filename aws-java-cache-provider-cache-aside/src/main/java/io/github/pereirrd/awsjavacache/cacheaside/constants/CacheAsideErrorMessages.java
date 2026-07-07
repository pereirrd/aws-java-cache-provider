package io.github.pereirrd.awsjavacache.cacheaside.constants;

public final class CacheAsideErrorMessages {

    private CacheAsideErrorMessages() {}

    public static final String ENTITY_CLASS_REQUIRED = "entityClass must not be null";
    public static final String CACHE_REGION_OR_KEY_REQUIRED =
            "Entity must declare @CacheRegion, @CacheName, or a class-level @CacheKey template";
    public static final String CACHE_KEY_TEMPLATE_REQUIRES_ID =
            "Class-level @CacheKey template must contain the {id} placeholder";
    public static final String CACHE_ID_NOT_FOUND =
            "Entity must expose an identifier via @CacheId, @CacheKey on field/getter, or jakarta.persistence.Id";
    public static final String CACHE_TTL_NOT_FOUND = "Entity must declare @CacheTtl on the class or id field";
}
