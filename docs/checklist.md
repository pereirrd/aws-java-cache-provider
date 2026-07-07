# Checklist — `aws-java-cache-provider`

Biblioteca Java com quatro estratégias de cache (*cache-aside*, *read-through*, *write-through*, *write-behind*) sobre **Amazon ElastiCache** (Redis ou Memcached) e **persistência plugável JPA** como fonte de verdade.

**Como usar este documento**

1. **§ 0.x** — Decisões já tomadas (referência fixa durante a implementação).
2. **Fase 1** — Tarefas de *bootstrap* do projeto Maven, dependências e qualidade.
3. **Fases 2–8** — Implementação: núcleo (`…-core`), JPA, uma fase por módulo de estratégia, release.

Marque `[x]` nas listas de tarefas conforme for concluindo.

---

## Índice

| Secção | Conteúdo |
|--------|------------|
| [Fase 0](#fase-0) | Decisões § 0.1–0.6 (produto, stack, módulos, testes) |
| [LocalStack](localstack.md) | Infraestrutura local, Community vs Pro, validação |
| [Testes de integração](integration-tests.md) | Perfis Maven, compose, guia IA/Docker |
| [Fase 1](#fase-1) | POM, tooling, dependências |
| [Fase 2](#fase-2) | API comum no `…-core` |
| [Fase 3](#fase-3) | Contrato CRUD genérico |
| [Fase 4](#fase-4) | *Cache-aside* (início das fases por estratégia) |
| [Fase 8](#fase-8) | Release e documentação |
| [Anexo A](#anexo-a) | Coordenadas Maven de referência |

---

<a id="fase-0"></a>

## Fase 0 — Decisões de escopo

Todas as subsecções abaixo estão **decididas**; funcionam como contrato de arquitetura.

**Resumo**

- [x] [§ 0.1](#01-provedor-de-cache-redis-ou-memcached-decidido) — Redis **ou** Memcached por aplicação (exclusão mútua).
- [x] [§ 0.2](#02-autenticação-e-tls-no-elasticache-decidido) — Senha + TLS configurável; sem IAM.
- [x] [§ 0.3](#03-stack-core-puro-java-e-aws-decidido) — Core puro Java + AWS; sem Spring na lib.
- [x] [§ 0.4](#04-contrato-da-camada-jpa-plugavel-decidido) — Interface CRUD genérica `<ID, M>`.
- [x] [§ 0.5](#05-estrutura-modular-da-biblioteca-decidido) — Módulo `…-core` + um artefacto por estratégia.
- [x] [§ 0.6](#06-testes-e-ambiente-local-decidido) — LocalStack para testes e ambiente local.

### 0.1 Provedor de cache: Redis ou Memcached (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Cobertura** | A lib suporta **ambos** os backends (ElastiCache Redis e Memcached). |
| **Por aplicação** | **Um único** backend ativo: Redis **ou** Memcached — nunca os dois em simultâneo. |
| **Configuração** | O consumidor escolhe o recurso (ex. `cache.provider=REDIS` \| `MEMCACHED`). A lib expõe **fábricas/clientes** só do provider escolhido; *wiring* em DI fica na aplicação. |
| **Exclusão mútua** | Validar na criação da configuração: não ativar os dois providers ao mesmo tempo. |

**Implicações:** API comum + adaptadores (`RedisCacheClient`, `MemcachedCacheClient`); em *runtime* só um caminho ativo; documentar no README “uma aplicação = um provider”.

### 0.2 Autenticação e TLS no ElastiCache (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Autenticação** | **Senha** (AUTH / password; equivalente Memcached conforme o engine). |
| **TLS** | Configurável globalmente **e** parâmetros de TLS passíveis de definir na **criação** do cliente (builder/fábrica). |
| **IAM** | **Fora de escopo.** |

**Implicações:** configuração em camadas (defaults → properties → override no builder); senha nunca em logs; sem dependências para IAM auth.

### 0.3 Stack: core puro Java e AWS (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Biblioteca** | **Core puro:** Java + AWS SDK (onde aplicável) + clientes Redis/Memcached. **Sem** Spring Boot / *starters* desta lib. |
| **DI / frameworks** | Agnóstico: Spring, Quarkus, etc. apenas na **aplicação**. |
| **Docs** | Exemplos com Spring só como ilustração, fora do artefacto Maven principal. |

**Implicações:** interfaces, builders, fábricas; sem `spring-boot-starter-*` no `core`; testes conforme [§ 0.6](#06-testes-e-ambiente-local-decidido).

### 0.4 Contrato da camada JPA plugável (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Contrato** | Interface **CRUD** orientada à persistência JPA (nome final na implementação). |
| **Genéricos** | `Repository<ID, M>` — **`ID`** = chave, **`M`** = entidade/modelo. |
| **Pacotes** | API pública em `…api`; implementações internas em `…internal`. |
| **Maven** | `jakarta.persistence-api` preferencialmente **`provided`**; Hibernate no consumidor ou `test`. |

**Implicações:** fechar operações mínimas (`findById`, `save`, `deleteById`, …); implementação com `EntityManager`/DAO **na aplicação**.

### 0.5 Estrutura modular da biblioteca (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Objetivo** | Cada estratégia é um **artefacto Maven próprio**; o consumidor declara **só** o que usar. |
| **Módulo comum** | **`…-core`**: fábricas Redis/Memcached, configuração (endpoints, TLS, timeouts, *pools*), serialização, exceções, utilitários. Módulos de estratégia **dependem do core**, não entre si. |
| **Consumo** | Dependência ao(s) módulo(s) de estratégia necessários (o core entra em geral por transitividade). |
| **JPA** | Contrato SPI / integração com persistência conforme o desenho final (p.ex. no `…-core` ou extensões). |

**Nomes sugeridos** *(ajustar `artifactId` real):*

| Módulo | Conteúdo |
|--------|----------|
| `…-core` | Partilhado por todas as estratégias |
| `…-cache-aside` | *Cache-aside* (+ anotações, se aplicável) |
| `…-read-through` | *Read-through* |
| `…-write-through` | *Write-through* |
| `…-write-behind` | *Write-behind* / *write-back* |

**Mapa fases ↔ módulos**

| Fase | Onde |
|------|------|
| [Fase 2](#fase-2) | `…-core` |
| [Fase 3](#fase-3) | SPI JPA + testes isolados |
| [Fases 4–7](#fase-4) | Um módulo por estratégia |
| [Fase 8](#fase-8) | README com coordenadas por módulo; *BOM* opcional no PAI |

---

### 0.6 Testes e ambiente local (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Stack** | **LocalStack** (Docker) para integração com serviços AWS nos testes e para **desenvolvimento local**. |
| **Objetivo** | AWS SDK v2 com *endpoint* apontando ao LocalStack; alinhar com credenciais/região de teste documentadas. |

**Checklist de implementação**

- [x] `org.testcontainers:testcontainers-junit-jupiter` + `org.testcontainers:testcontainers-localstack` (profile Maven `integration`).
- [x] *Endpoint override* via `AwsSdkEnvConfig` / `AwsSdkClientFactory`; região e credenciais por variáveis de ambiente.
- [x] `docker-compose` + `.env.example` para dev manual; README com `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_DEFAULT_REGION`, URL do endpoint.
- [x] Serviços LocalStack declarados e **validados** (Secrets Manager, STS, CloudWatch em Community; ElastiCache API documentada como **Pro only** — ver [`docs/localstack.md`](localstack.md)).
- [x] Documentação de infraestrutura local e limites Community vs Pro.
- [x] JUnit 5; AssertJ recomendado.

> **Nota:** tráfego **Redis/Memcached** (Lettuce/Spymemcached) pode usar endpoint de teste ou o exposto pelo stack; alinhar testes de protocolo ao que for suportado.

---

<a id="fase-1"></a>

## Fase 1 — Bootstrap do projeto (Java 25 + Maven)

Tarefas executáveis para criar o repositório e o *classpath*. Decisões de módulos e LocalStack: [§ 0.5](#05-estrutura-modular-da-biblioteca-decidido), [§ 0.6](#06-testes-e-ambiente-local-decidido).

### 1.1 Estrutura e build

- [x] `pom` **pai** (`packaging` `pom`) + módulos filhos: **`…-core`**, **`…-cache-aside`**, **`…-read-through`**, **`…-write-through`**, **`…-write-behind`** (+ opcional JPA/SPI). [§ 0.1](#01-provedor-de-cache-redis-ou-memcached-decidido): um provider Redis ou Memcached por aplicação em *runtime*.
- [x] **Java 25** (`maven.compiler.release` = `25`; *plugin* compatível).
- [x] **Maven Wrapper** (`mvnw`, `.mvn/wrapper`; *distributionType* `only-script`).
- [x] `groupId`, `artifactId`, `version`, convenção de pacotes.
- [x] Surefire (e opcionalmente Failsafe).

### 1.2 Padrões de código e documentação

- [x] `.editorconfig` / formatação (Spotless, fmt-maven-plugin ou Checkstyle).
- [x] `README`: pré-requisitos, compilar, publicar artefactos.
- [x] `LICENSE`, se aplicável.

### 1.3 Dependências — AWS (SDK for Java 2.x)

*BOM* `software.amazon.awssdk:bom`; incluir só o necessário.

- [x] `software.amazon.awssdk:elasticache` (*control plane*; não substitui cliente de dados).
- [x] `auth`, `regions` (se controlo explícito).
- [x] Opcional: `sts`, `secretsmanager`, `cloudwatch`.

> Tráfego de **dados** Redis/Memcached continua em clientes de protocolo ([§ 1.4](#14-dependências--clientes-de-protocolo-dados)); o SDK AWS cobre gestão e integrações em redor do cluster.

### 1.4 Dependências — clientes de protocolo (dados)

- [x] **Redis:** `io.lettuce:lettuce-core` *ou* `redis.clients:jedis`.
- [x] **Memcached:** `net.spy:spymemcached` (validar compatibilidade ElastiCache).

### 1.5 Dependências — JPA

- [x] `jakarta.persistence:jakarta.persistence-api` — preferir **`provided`** ([§ 0.4](#04-contrato-da-camada-jpa-plugavel-decidido)).
- [x] Hibernate **não** obrigatório no `core`; `test` ou consumidor.
- [x] Sem `spring-boot-starter-data-jpa` na lib.

---

<a id="fase-2"></a>

## Fase 2 — Núcleo API comum (módulo `…-core`)

[§ 0.5](#05-estrutura-modular-da-biblioteca-decidido).

- [x] Interfaces: fábrica de conexão (Redis vs Memcached), configuração (endpoints, TLS, timeouts, *pool*).
- [x] Modelo chave/valor (serialização via `CacheValueSerializer`; limites por engine a documentar no consumidor).
- [x] Exceções base (`CacheException`; *timeouts* / indisponibilidade a expandir).
- [x] Métricas e *hooks* opcionais (hit/miss, latência) — `CacheMetrics` em `…-core`, integrado nas estratégias.
- [x] Documentar **TTL** por estratégia — [`docs/ttl-por-estrategia.md`](ttl-por-estrategia.md).

---

<a id="fase-3"></a>

## Fase 3 — Camada JPA (fonte de verdade)

[§ 0.4](#04-contrato-da-camada-jpa-plugavel-decidido); módulo SPI/contrato, sem poluir o `core`.

- [x] Formalizar interface CRUD `<ID, M>` (`BackingRepository` em `…core`: `findById`, `save`, `deleteById`); **sem** `EntityManager` na API pública.
- [x] Documentar implementação pelo consumidor (README *cache-aside*; JPA/Spring Data na app).
- [x] *Write-through* / *write-behind*: contrato transacional do repositório; `@Transactional` só em exemplos na **app** (*write-through*: ordem origem→cache documentada) — [`docs/contrato-transacional.md`](contrato-transacional.md).
- [x] Testes de integração BD (H2 / Testcontainers PostgreSQL/MySQL) **só** no módulo JPA (`…-jpa`, H2 em `JpaBackingRepositoryIT`).

---

<a id="fase-4"></a>

## Fase 4 — Estratégia: *cache-aside* (módulo `…-cache-aside`)

Depende de **`…-core`**. [§ 0.5](#05-estrutura-modular-da-biblioteca-decidido).

- [x] Fluxo: `get` → *miss* → `BackingRepository#findById` → `put` com TTL (`CacheAsideService`).
- [ ] **Anotações** Java (chave, TTL, id de cache); processamento em runtime opcional / na app ([§ 0.3](#03-stack-core-puro-java-e-aws-decidido)).
- [x] API de invalidação/atualização explícita (`evict`, `putCached`; README).
- [x] Testes unitários (dublês de `CacheProvider` + repositório).
- [x] Testes de integração: [§ 0.6](#06-testes-e-ambiente-local-decidido) (`mvn verify -Pintegration` no `…-core` e `…-cache-aside`).

---

## Fase 5 — Estratégia: *read-through* (módulo `…-read-through`)

Depende de **`…-core`**.

- [x] Leitura só via cache; *loader* em *miss* (estilo *LoadingCache*).
- [x] Uma carga por chave sob concorrência (*single loader* / *lock*).
- [x] Testes de concorrência.
- [x] Integração: [§ 0.6](#06-testes-e-ambiente-local-decidido) (`ReadThroughServiceComposeIT`).

---

## Fase 6 — Estratégia: *write-through* (módulo `…-write-through`)

Depende de **`…-core`**.

- [x] Escrita coordenada: origem (JPA/CRUD) + cache; definir ordem e falha parcial.
- [x] Testes com repositório fake + [§ 0.6](#06-testes-e-ambiente-local-decidido) (`WriteThroughServiceComposeIT`).

---

## Fase 7 — Estratégia: *write-behind* (módulo `…-write-behind`)

Depende de **`…-core`**.

- [x] Fila + executor + *batch* para a origem; política de durabilidade da fila documentada (fila em memória na JVM; perda possível antes de `flush`/`close`).
- [x] Idempotência na origem (requisitos ao JPA documentados em `WriteBehindTask` / README).
- [x] *Backpressure* e métricas (`WriteBehindConfig`, `WriteBehindMetrics`, `WriteBehindQueueStats`).
- [x] Testes de carga leve, *shutdown* com *flush* (`WriteBehindServiceTest`).
- [x] Integração: [§ 0.6](#06-testes-e-ambiente-local-decidido) (`WriteBehindServiceComposeIT`).

---

<a id="fase-8"></a>

## Fase 8 — Release e documentação

- [x] README: snippet *cache-aside*; **coordenadas Maven por módulo** ([§ 0.5](#05-estrutura-modular-da-biblioteca-decidido)) (*read-through*, *write-through* e *write-behind* documentados).
- [x] CI: `mvn verify`, JDK 25, cache Maven (GitHub Actions).
- [ ] Versionamento semântico; Maven Central / GitHub Packages; `distributionManagement`.
- [ ] Segurança: OWASP Dependency-Check / Dependabot.

---

<a id="anexo-a"></a>

## Anexo A — Artefactos Maven

| Finalidade | Coordenadas (exemplo) |
|------------|------------------------|
| BOM AWS SDK v2 | `software.amazon.awssdk:bom` |
| ElastiCache (*control plane*) | `software.amazon.awssdk:elasticache` |
| Cliente Redis | `io.lettuce:lettuce-core` ou `redis.clients:jedis` |
| Cliente Memcached | `net.spy:spymemcached` |
| JPA API | `jakarta.persistence:jakarta.persistence-api` |
| Testes | `org.junit.jupiter:junit-jupiter`, `org.testcontainers:testcontainers`, `org.testcontainers:localstack` |

*Ajustar versões ao BOM AWS e ao JDK 25.*

---

*Atualizar `artifactId` dos módulos, pacotes e escolha Redis/Memcached quando fixados ([§ 0.5](#05-estrutura-modular-da-biblioteca-decidido)).*
