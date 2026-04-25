# aws-java-cache-provider

Biblioteca Java com estratégias de cache (*cache-aside*, *read-through*, *write-through*, *write-behind*) sobre **Amazon ElastiCache** (Redis ou Memcached) e persistência JPA plugável.

## Pré-requisitos

- **JDK 25** (definido em `maven.compiler.release` no POM raiz).
- **Apache Maven** instalado e `JAVA_HOME` apontando para o JDK 25.

## Compilar

Na raiz do repositório (Maven instalado ou `./mvnw`):

```bash
mvn clean verify
```

A fase `verify` inclui formatação (Spotless). Para aplicar o formato sem falhar o build:

```bash
mvn spotless:apply
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
