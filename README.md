# aws-java-cache-provider

Biblioteca Java com estratégias de cache (*cache-aside*, *read-through*, *write-through*, *write-behind*) sobre **Amazon ElastiCache** (Redis ou Memcached) e persistência JPA plugável.

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

## Módulos (coordenadas Maven)

| Módulo | `artifactId` |
|--------|----------------|
| Núcleo partilhado | `aws-java-cache-provider-core` |
| Cache-aside | `aws-java-cache-provider-cache-aside` |
| Read-through | `aws-java-cache-provider-read-through` |
| Write-through | `aws-java-cache-provider-write-through` |
| Write-behind | `aws-java-cache-provider-write-behind` |

`groupId`: `io.github.pereirrd.awsjavacache` · `version`: ver o POM raiz (ex.: `0.1.0-SNAPSHOT`).

Exemplo de dependência ao usar só *cache-aside* (o `core` entra por transitividade):

```xml
<dependency>
  <groupId>io.github.pereirrd.awsjavacache</groupId>
  <artifactId>aws-java-cache-provider-cache-aside</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Publicar artefactos

1. Configurar `distributionManagement` e credenciais no `settings.xml` do Maven (Sonatype OSSRH / GitHub Packages / repositório interno).
2. Garantir assinatura GPG e `javadoc`/`sources` conforme os requisitos do alvo de publicação.
3. Executar o *release* Maven adequado (ex.: `mvn deploy` ou fluxo `release:prepare` / `release:perform`).

Detalhes de CI e *release* semântico estão planeados na Fase 8 do `docs/checklist.md`.

## Licença

Ver [LICENSE](LICENSE) (Apache License 2.0).
