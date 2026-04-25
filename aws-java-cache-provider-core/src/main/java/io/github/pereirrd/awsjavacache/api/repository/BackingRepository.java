package io.github.pereirrd.awsjavacache.api.repository;

import java.util.Optional;

/**
 * Pluggable persistence used as source of truth for cache-aside and related strategies.
 * Implemented by the application (JPA, JDBC, etc.); no {@code EntityManager} on this API.
 *
 * @param <ID> identifier type
 * @param <M> entity or domain model type
 */
public interface BackingRepository<ID, M> {

    Optional<M> findById(ID id);

    M save(M entity);

    void deleteById(ID id);
}
