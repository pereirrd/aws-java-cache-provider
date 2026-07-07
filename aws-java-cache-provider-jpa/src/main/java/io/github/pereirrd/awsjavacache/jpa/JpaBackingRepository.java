package io.github.pereirrd.awsjavacache.jpa;

import io.github.pereirrd.awsjavacache.api.repository.BackingRepository;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link BackingRepository} backed by Jakarta Persistence {@link EntityManager}.
 *
 * <p>Does not begin or commit transactions — the consumer must demarcate transactional scope
 * (for example {@code @Transactional} in the application layer). See {@code docs/contrato-transacional.md}.
 *
 * @param <ID> identifier type (must match the entity {@code @Id} field)
 * @param <M> JPA entity type
 */
public final class JpaBackingRepository<ID, M> implements BackingRepository<ID, M> {

    private final EntityManager entityManager;
    private final Class<M> entityClass;

    public JpaBackingRepository(EntityManager entityManager, Class<M> entityClass) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
    }

    @Override
    public Optional<M> findById(ID id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(entityManager.find(entityClass, id));
    }

    @Override
    public M save(M entity) {
        Objects.requireNonNull(entity, "entity");
        var merged = entityManager.merge(entity);
        entityManager.flush();
        return merged;
    }

    @Override
    public void deleteById(ID id) {
        Objects.requireNonNull(id, "id");
        findById(id).ifPresent(entityManager::remove);
        entityManager.flush();
    }
}
