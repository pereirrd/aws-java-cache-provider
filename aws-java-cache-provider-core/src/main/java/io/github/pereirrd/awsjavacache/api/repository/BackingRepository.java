package io.github.pereirrd.awsjavacache.api.repository;

import java.util.Optional;

/**
 * Pluggable persistence used as source of truth for cache-aside and related strategies.
 * Implemented by the application (JPA, JDBC, etc.); no {@code EntityManager} on this API.
 *
 * <p><strong>Transaction boundaries:</strong> this library does not open or commit transactions.
 * The consumer (application layer) must demarcate transactional scope — for example {@code @Transactional}
 * in Spring or explicit {@code EntityManager} transactions — so that {@link #save} and {@link #deleteById}
 * participate in the correct unit of work. See {@code docs/contrato-transacional.md}.
 *
 * @param <ID> identifier type
 * @param <M> entity or domain model type
 */
public interface BackingRepository<ID, M> {

    Optional<M> findById(ID id);

    M save(M entity);

    void deleteById(ID id);
}
