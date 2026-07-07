# Contrato transacional — repositório de origem e estratégias de escrita

Este documento descreve como a biblioteca coordena leituras e escritas entre **cache** e **fonte de verdade** (`BackingRepository`), e onde ficam as **fronteiras transacionais**. A lib é **core puro Java** — não inclui Spring, JTA nem `@Transactional` nos artefactos Maven.

Referências de código:

| Componente | Módulo | Papel |
|------------|--------|-------|
| [`BackingRepository`](../aws-java-cache-provider-core/src/main/java/io/github/pereirrd/awsjavacache/api/repository/BackingRepository.java) | `…-core` | SPI CRUD plugável (JPA, JDBC, etc.) |
| [`WriteThroughService`](../aws-java-cache-provider-write-through/src/main/java/io/github/pereirrd/awsjavacache/writethrough/WriteThroughService.java) | `…-write-through` | Escrita síncrona origem → cache |
| [`WriteBehindService`](../aws-java-cache-provider-write-behind/src/main/java/io/github/pereirrd/awsjavacache/writebehind/WriteBehindService.java) | `…-write-behind` | Escrita assíncrona cache → origem (fila) |
| [`JpaBackingRepository`](../aws-java-cache-provider-jpa/src/main/java/io/github/pereirrd/awsjavacache/jpa/JpaBackingRepository.java) | `…-jpa` | Implementação JPA de referência (sem Spring) |

---

## Responsabilidade transacional

A biblioteca **não** abre, propaga nem faz *commit* de transações de persistência. Cada chamada a `BackingRepository#save`, `#deleteById` ou `#findById` executa no contexto transacional **já activo** na thread do consumidor.

| Camada | Responsabilidade |
|--------|------------------|
| **Aplicação** | Demarcar transações (`@Transactional`, `UserTransaction`, `EntityManager.getTransaction()`, etc.) |
| **Lib (estratégias)** | Orquestrar cache + repositório na ordem documentada por estratégia |
| **Lib (`…-jpa`)** | Delegar CRUD ao `EntityManager`; o consumidor gere o `EntityManager`/transacção |

### `@Transactional` só na aplicação

Exemplos abaixo ilustram o **consumidor** — **não** fazem parte dos JARs publicados.

**Spring (write-through):**

```java
@Service
public class UserService {

    private final WriteThroughService<Long, UserEntity> writeThrough;

    @Transactional
    public UserEntity update(UserEntity user) {
        return writeThrough.save(user); // origem (JPA) + cache na mesma unidade lógica
    }
}
```

**Jakarta Persistence explícito (write-behind com flush antes do shutdown):**

```java
void shutdown() {
    writeBehind.flush(); // drenar fila para a origem
    writeBehind.close();
}
```

---

## Write-through — ordem origem → cache

[`WriteThroughService`](../aws-java-cache-provider-write-through/src/main/java/io/github/pereirrd/awsjavacache/writethrough/WriteThroughService.java) garante:

| Operação | Ordem | Comportamento |
|----------|-------|---------------|
| `save(entity)` | 1. `repository.save` → 2. `cache.put` | Só actualiza o cache após persistência na origem |
| `deleteById(id)` | 1. `repository.deleteById` → 2. `cache.invalidate` | Só invalida após remoção na origem |
| `get(id)` | cache → (miss) → `repository.findById` → `cache.put` | Leitura; não altera transacção de escrita |

### Política de falha parcial

- **Origem falha:** a operação propaga a excepção; o cache **não** é alterado.
- **Origem OK, cache falha:** lança `CacheException`; a origem já foi persistida. A entrada no cache pode ficar **desactualizada** até `evict(id)` ou um `save` bem-sucedido.
- **Compensação:** responsabilidade da aplicação (retry, invalidação manual, alertas).

Recomendação: envolver `save`/`deleteById` numa transacção de origem **antes** de confiar no cache para leituras subsequentes na mesma request; para consistência cross-request, confiar no TTL + invalidação ou monitorizar falhas de cache.

---

## Write-behind — cache à frente, origem assíncrona

[`WriteBehindService`](../aws-java-cache-provider-write-behind/src/main/java/io/github/pereirrd/awsjavacache/writebehind/WriteBehindService.java) garante:

| Operação | Ordem | Comportamento |
|----------|-------|---------------|
| `save(entity)` | 1. `cache.put` → 2. enfileira `repository.save` | Resposta rápida; persistência em background |
| `deleteById(id)` | 1. `cache.invalidate` → 2. enfileira `repository.deleteById` | Cache reflecte remoção antes da origem |
| `get(id)` | cache → (miss) → `repository.findById` | Pode ler valor ainda não flushado se miss e origem atrasada |

### Idempotência na origem

A fila é **FIFO em memória**; falhas no *flush*, *retries* ou `close()` podem **repetir** operações. O consumidor deve implementar `BackingRepository` de forma **idempotente**:

- **`save`:** `merge` por chave natural ou versão; upsert seguro.
- **`deleteById`:** remoção idempotente (no-op se já ausente).

Ver também [`WriteBehindTask`](../aws-java-cache-provider-write-behind/src/main/java/io/github/pereirrd/awsjavacache/writebehind/WriteBehindTask.java).

### Cache à frente da origem

Após `save`/`deleteById`, o cache reflecte o estado **optimista**. A origem pode ficar temporariamente desalinhada até o processador drenar a fila. Em *backpressure* (fila cheia), o cache **já foi actualizado** mas a operação na origem **não** foi enfileirada — ver `WriteBehindService` e métricas `WriteBehindQueueStats`.

### Durabilidade

Chamar `flush()` ou `close()` antes de desligar a JVM para drenar pendentes. Perda de fila = perda de escritas ainda não aplicadas na origem.

---

## Cache-aside e read-through

Estas estratégias **não** escrevem na origem. Transacções de persistência ficam **100%** na aplicação; após mutações, usar `evict` / `putCached` (cache-aside) ou política de TTL documentada em [`ttl-por-estrategia.md`](ttl-por-estrategia.md).

---

## Integração JPA (`…-jpa`)

[`JpaBackingRepository`](../aws-java-cache-provider-jpa/src/main/java/io/github/pereirrd/awsjavacache/jpa/JpaBackingRepository.java) implementa `BackingRepository` com `EntityManager` **injected** pelo consumidor:

- **Produção:** uma transacção por request (Spring `@Transactional`, CDI `@Transactional`, etc.) envolve serviço + repositório.
- **Testes:** transacção explícita ou `EntityManager` por teste (ver `JpaBackingRepositoryIT`).

`jakarta.persistence-api` é `provided`; Hibernate fica no consumidor ou em `test` no módulo JPA.

---

*Documento de referência — Fase 3. Actualizar quando novas estratégias ou políticas de falha forem introduzidas.*
