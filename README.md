# aws-java-cache-provider

Biblioteca Java com estratégias de cache (*cache-aside*, *read-through*, *write-through*, *write-behind*) sobre **Amazon ElastiCache** (Redis ou Memcached) e persistência JPA plugável. O código está funcional e coberto por testes unitários e de integração (ver [`docs/integration-tests.md`](docs/integration-tests.md)), mas **não foi utilizado em nenhuma aplicação real de produção** — não há validação de escalabilidade, consumo de recursos, SLOs nem operação prolongada sob carga.

## Caráter educativo

Este projeto tem **somente finalidade educativa e experimental**. Não é um produto comercial nem uma biblioteca recomendada para produção. A intenção era somente testar a implementação de diferentes estratégias de cache usando funcionalidades do Cursor, como sub-agents e cloud agents, pela IDE e por interface web acessando pelo celular.

## Finalização do projeto

A implementação foi concluída como **experiência de desenvolvimento assistido por IA** no ecossistema Cursor:

| Aspecto | Descrição |
|---------|-----------|
| **Subagentes Cursor** | Tarefas divididas em fases (`CacheMetrics`, JPA, anotações, integração) executadas por subagentes locais com branches e commits independentes |
| **Agents na interface web** | Parte do trabalho foi coordenada a partir da **interface web do Cursor**, com agentes acionados **pelo celular** (comando remoto, revisão e orientação fora do IDE desktop) |
| **Spec-driven development** | Escopo, checklist (`docs/checklist.md`) e decisões de arquitetura (§ 0.x) guiaram a implementação fase a fase |
| **Estado funcional** | `mvn clean verify` e perfis `-Pintegration` / `-Pintegration-compose` validam o comportamento contra Redis, LocalStack e H2 (módulo JPA) |
| **Fora de escopo** | Publicação em Maven Central, OWASP/Dependabot e uso em produção com métricas de recurso |

**Para sistemas reais em produção**, recomendamos ecossistemas com *cache providers* maduros, observabilidade integrada e comunidade ativa, por exemplo:

- **[Spring Boot](https://spring.io/projects/spring-boot)** — `spring-boot-starter-cache`, integração com Redis/Caffeine/Hazelcast, `@Cacheable` / `@CacheEvict`, métricas via Actuator/Micrometer
- **[Micronaut](https://micronaut.io/)** — cache declarativo, suporte a Redis e Caffeine, baixa pegada de memória e bom encaixe em microsserviços

Este projeto serve para estudar padrões de cache, integração com ElastiCache e fluxos de desenvolvimento com agentes de IA — **não** como substituto dessas soluções de mercado.

Documentação de encerramento: [`docs/release.md`](docs/release.md) · checklist completo: [`docs/checklist.md`](docs/checklist.md).

## Pré-requisitos

- **JDK 25** (definido em `maven.compiler.release` no POM raiz).
- **Apache Maven** instalado e `JAVA_HOME` apontando para o JDK 25.
- **Docker** e **Docker Compose** (opcional, para ambiente local com LocalStack).

## Ambiente local (LocalStack)

A infraestrutura local emula APIs AWS de *control plane* (Secrets Manager, CloudWatch, STS; ElastiCache API com **LocalStack Pro**) via **LocalStack**, e expõe **Redis** (e opcionalmente **Memcached**) para o tráfego de **dados** do cache.

Documentação completa: [`docs/localstack.md`](docs/localstack.md) (arquitetura, matriz Community vs Pro) e [`docs/integration-tests.md`](docs/integration-tests.md) (perfis Maven, compose, guia para IA).

1. Copie o ficheiro de variáveis:

```bash
cp .env.example .env
```

2. Suba os serviços:

```bash
docker compose up -d
```

Para incluir Memcached:

```bash
docker compose --profile memcached up -d
```

3. Exporte as variáveis (ou use ferramentas como `direnv` / `source .env` conforme o seu shell).

| Variável | Uso |
|----------|-----|
| `AWS_JAVA_CACHE_LOCALSTACK_ENABLED` | `true` para apontar o AWS SDK ao LocalStack |
| `AWS_JAVA_CACHE_LOCALSTACK_HOST` / `PORT` | Gateway LocalStack (default `localhost:4566`) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Credenciais (LocalStack aceita `test`/`test`) |
| `AWS_DEFAULT_REGION` | Região (ex.: `us-east-1`) |
| `AWS_JAVA_CACHE_AWS_ENDPOINT_URL` | Override explícito do endpoint AWS SDK |
| `AWS_JAVA_CACHE_REDIS_*` | Endpoint Redis local (`localhost:6379`) |

Validação rápida:

```bash
curl -s http://localhost:4566/_localstack/health
docker exec aws-java-cache-redis redis-cli ping
docker exec aws-java-cache-localstack awslocal secretsmanager get-secret-value \
  --secret-id aws-java-cache/local/redis-password
```

Na aplicação Java:

```java
var awsConfig = AwsSdkEnvConfig.fromEnvironment();
try (var secrets = AwsSdkClientFactory.secretsManager(awsConfig)) {
    // Secrets Manager, STS, CloudWatch — OK em Community
}
// ElastiCache API: requer LocalStack Pro (ver docs/localstack.md)
var redis = RedisCacheClientFactory.fromEnvironment();
```

> **Nota:** `mvn clean verify` continua a usar apenas *stubs* em memória — **não** requer Docker.
> Testes de integração: ver [`docs/integration-tests.md`](docs/integration-tests.md).

> **ElastiCache API:** na edição **Community** do LocalStack 4.4 a API não está emulada (`DescribeCacheClusters` falha). Para *control plane* ElastiCache use **LocalStack Pro**; para desenvolvimento local do cache use o container **Redis** do `docker-compose`.

## Compilar

Na raiz do repositório (Maven instalado ou `./mvnw`):

```bash
mvn clean verify
```

A fase `verify` inclui formatação (Spotless). Para aplicar o formato sem falhar o build:

```bash
mvn spotless:apply
```

### Testes de integração

Resumo (detalhes em [`docs/integration-tests.md`](docs/integration-tests.md)):

```bash
# Stack manual + testes (recomendado se Docker API indisponível — ex.: agente IA)
docker compose up -d
set -a && source .env && set +a
mvn clean verify -Pintegration-compose

# Testcontainers (requer docker ps sem permission denied)
set -a && source .env && set +a
mvn clean verify -Pintegration
```

## Cache-aside (uso programático)

1. Obtenha um `CacheProvider` a partir do `core` (Redis ou Memcached via fábricas / CDI).
2. Implemente `BackingRepository<ID, M>` na aplicação (JPA, JDBC, cliente HTTP, etc.).
3. Forneça um `CacheValueSerializer<M>` (por exemplo identidade para `String` com `CacheValueSerializer.utf8Strings()`, ou JSON com Jackson no consumidor).
4. Instancie `CacheAsideService` com TTL de entrada e uma função `ID → chave de cache`.

```java
CacheProvider cache = RedisCacheProvider.utf8Strings(RedisCacheClientFactory.fromEnvironment());
Duration ttl = Duration.ofMinutes(10);
CacheAsideService<Long, User> service = new CacheAsideService<>(
    cache,
    userRepository, // sua implementação de BackingRepository<Long, User>
    id -> "users:" + id,
    userSerializer, // CacheValueSerializer<User> (ex.: JSON na aplicação)
    ttl);

Optional<User> user = service.get(42L);
// Após gravar no repositório:
service.evict(42L);
// ou atualizar o cache explicitamente:
service.putCached(42L, updatedUser);
```

## Cache-aside (anotações)

Para evitar repetir função de chave e TTL no código, declare metadados na classe da entidade e use
`CacheAsideAnnotationResolver` ou `CacheAsideService.fromAnnotations`. Não há AOP nem bytecode weaving: a
aplicação invoca o resolver explicitamente ([§ 0.3](docs/checklist.md#03-stack-core-puro-java-e-aws-decidido)).

| Anotação | Onde | Função |
|----------|------|--------|
| `@CacheRegion` / `@CacheName` | classe | namespace (ex.: `"users"` → chave `users:{id}`) |
| `@CacheKey` | classe, campo ou getter | template com `{id}` na classe; marca o identificador no campo/getter |
| `@CacheTtl` | classe ou campo id | TTL de entrada (`value` + `unit`, default 5 minutos) |
| `@CacheId` | campo ou getter | identificador quando não há `jakarta.persistence.Id` |

```java
@CacheRegion("users")
@CacheTtl(value = 10, unit = TimeUnit.MINUTES)
public class User {
    @CacheId
    private Long id;
    private String name;
    // getters...
}

CacheAsideService<Long, User> service = CacheAsideService.fromAnnotations(
    cache, userRepository, User.class, userSerializer);

// Ou só metadados (chave + TTL + extrator de id):
@SuppressWarnings("unchecked")
CacheAsideMetadata<Long> metadata =
    (CacheAsideMetadata<Long>) CacheAsideAnnotationResolver.resolve(User.class);
String key = metadata.cacheKeyForId().apply(42L); // "users:42"
```

Com template explícito: `@CacheKey("profile:{id}")` na classe. O resolver também reconhece
`jakarta.persistence.Id` via reflexão (sem dependência JPA no módulo cache-aside).

## Read-through (uso programático)

1. Obtenha um `CacheProvider` a partir do `core` (Redis ou Memcached via fábricas / CDI).
2. Implemente `BackingRepository<ID, M>` na aplicação.
3. Forneça um `CacheValueSerializer<M>`.
4. Instancie `ReadThroughService` — todas as leituras passam por ele; em *miss* o serviço carrega da origem com *single-flight* por chave.

```java
CacheProvider cache = RedisCacheProvider.utf8Strings(RedisCacheClientFactory.fromEnvironment());
Duration ttl = Duration.ofMinutes(10);
ReadThroughService<Long, User> service = new ReadThroughService<>(
    cache,
    userRepository,
    id -> "users:" + id,
    userSerializer,
    ttl);

Optional<User> user = service.get(42L);
// Após gravar no repositório:
service.evict(42L);
```

## Write-through (uso programático)

1. Obtenha um `CacheProvider` a partir do `core`.
2. Implemente `BackingRepository<ID, M>` na aplicação.
3. Forneça `CacheValueSerializer<M>`, função `ID → chave de cache` e `M → ID` (para resolver a chave após `save`).
4. Instancie `WriteThroughService` — gravações vão **primeiro** à origem e **depois** ao cache; leituras passam pelo cache com carga em *miss*.

```java
CacheProvider cache = RedisCacheProvider.utf8Strings(RedisCacheClientFactory.fromEnvironment());
Duration ttl = Duration.ofMinutes(10);
WriteThroughService<Long, User> service = new WriteThroughService<>(
    cache,
    userRepository,
    id -> "users:" + id,
    User::getId,
    userSerializer,
    ttl);

User saved = service.save(newUser);
service.deleteById(42L);
Optional<User> user = service.get(42L);
```

Se a origem gravar com sucesso mas o cache falhar, o serviço lança `CacheException` — a origem permanece correta; use `evict` ou repita `save` para reconciliar o cache.

## Write-behind (uso programático)

1. Obtenha um `CacheProvider` a partir do `core`.
2. Implemente `BackingRepository<ID, M>` na aplicação de forma **idempotente** (`save` / `deleteById` seguros para reprocessamento).
3. Forneça `CacheValueSerializer<M>`, função `ID → chave de cache` e `M → ID`.
4. Instancie `WriteBehindService` — gravações atualizam o **cache primeiro** e enfileiram persistência assíncrona na origem; leituras passam pelo cache com carga em *miss*.

```java
CacheProvider cache = RedisCacheProvider.utf8Strings(RedisCacheClientFactory.fromEnvironment());
Duration ttl = Duration.ofMinutes(10);
WriteBehindConfig config = WriteBehindConfig.defaults(); // fila em memória, batch, flush periódico
WriteBehindService<Long, User> service = new WriteBehindService<>(
    cache,
    userRepository,
    id -> "users:" + id,
    User::getId,
    userSerializer,
    ttl,
    config,
    WriteBehindMetrics.NO_OP);

User saved = service.save(newUser); // cache atualizado; origem em background
service.deleteById(42L);
Optional<User> user = service.get(42L);
service.flush(); // drena a fila antes de shutdown ou testes
service.close(); // flush + encerra o processador
```

**Durabilidade:** a fila de escritas pendentes vive só na JVM. Em crash antes de `flush()` / `close()`, operações enfileiradas podem perder-se mesmo com o cache já atualizado.

**Backpressure:** quando a fila enche, `save` / `deleteById` lançam `CacheException` após o efeito no cache; ajuste `WriteBehindConfig#queueCapacity` ou chame `flush()`.

## Observabilidade (`CacheMetrics`)

Todos os serviços de estratégia aceitam um `CacheMetrics` opcional (default `CacheMetrics.NO_OP`) para *hooks* de hit/miss, carga na origem, `put` e `evict`, com latência por operação.

```java
CacheMetrics metrics = new CacheMetrics() {
    @Override
    public void onCacheHit(String cacheKey, Duration latency) {
        // ex.: exportar para Micrometer na aplicação
    }

    @Override
    public void onCacheMiss(String cacheKey, Duration latency) {
        // ...
    }
};

CacheAsideService<Long, User> service = new CacheAsideService<>(
    cache, userRepository, id -> "users:" + id, userSerializer, ttl, metrics);
```

Em *write-behind*, `WriteBehindMetrics` cobre fila e *flush*; `CacheMetrics` cobre operações de leitura/escrita no cache (parâmetro adicional no construtor completo).

Detalhes de TTL por estratégia: [`docs/ttl-por-estrategia.md`](docs/ttl-por-estrategia.md).

## Módulos (coordenadas Maven)

| Módulo | `artifactId` |
|--------|----------------|
| Núcleo partilhado | `aws-java-cache-provider-core` |
| Cache-aside | `aws-java-cache-provider-cache-aside` |
| Read-through | `aws-java-cache-provider-read-through` |
| Write-through | `aws-java-cache-provider-write-through` |
| Write-behind | `aws-java-cache-provider-write-behind` |
| JPA (exemplo `BackingRepository`) | `aws-java-cache-provider-jpa` |

`groupId`: `io.github.pereirrd.awsjavacache` · `version`: ver o POM raiz (ex.: `0.1.0-SNAPSHOT`).

> **Nota:** os artefactos **não são publicados** em repositório Maven remoto; use `mvn clean install` localmente se quiser consumir os JARs no seu ambiente.

Exemplo de dependência ao usar só *cache-aside* (o `core` entra por transitividade):

```xml
<dependency>
  <groupId>io.github.pereirrd.awsjavacache</groupId>
  <artifactId>aws-java-cache-provider-cache-aside</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Publicar artefactos

**Não previsto neste projeto.** Por decisão de escopo (projeto educativo), não há `distributionManagement` nem pipeline de deploy. Para referência futura sobre versionamento semântico, ver [`docs/release.md`](docs/release.md).

## Licença

Ver [LICENSE](LICENSE) (Apache License 2.0).
