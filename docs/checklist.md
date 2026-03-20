# Checklist — `aws-java-cache-provider`

Biblioteca Java que implementa **cache-aside**, **read-through**, **write-through** e **write-behind / write-back** sobre **Amazon ElastiCache** (Redis ou Memcached), com **persistência plugável** via extensões **JPA** como fonte de verdade quando aplicável.

Marque `[x]` conforme for concluindo.

---

## Fase 0 — Decisões de escopo (antes do código)

- [x] **Provedor de cache (Redis vs Memcached)** — ver [§ 0.1](#01-provedor-de-cache-redis-ou-memcached-decidido).
- [x] **Autenticação e TLS no ElastiCache** — ver [§ 0.2](#02-autenticação-e-tls-no-elasticache-decidido).
- [x] **Framework: core puro (Java + AWS)** — ver [§ 0.3](#03-stack-core-puro-java-e-aws-decidido).
- [x] **Camada plugável JPA (contrato)** — ver [§ 0.4](#04-contrato-da-camada-jpa-plugavel-decidido).

### 0.1 Provedor de cache: Redis ou Memcached (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Cobertura da biblioteca** | A lib **suporta os dois** backends (ElastiCache para **Redis** e para **Memcached**). |
| **Por aplicação** | Em cada deploy / aplicação cliente existe **um único** backend ativo: ou Redis **ou** Memcached — **nunca os dois em simultâneo**. |
| **Configuração** | O consumidor define **qual recurso** usar (ex.: propriedade `cache.provider=REDIS` \| `MEMCACHED`, ou equivalente). Com base nisso, a lib expõe **fábricas/clientes** apenas do provider escolhido; o *wiring* em DI (Spring, CDI, etc.) fica na aplicação. |
| **Exclusão mútua** | Não é permitido ativar Redis e Memcached **ao mesmo tempo**. Escolher um **impede** o outro (validação na criação da configuração ou regras equivalentes). |

**Implicações de implementação (rascunho):**

- Uma **API comum** (interfaces do núcleo) implementada por **dois adaptadores** internos (`RedisCacheClient`, `MemcachedCacheClient`), com registo condicional.
- **Classpath:** o artefacto Maven pode incluir dependências opcionais (`lettuce` vs `spymemcached`) ou módulos separados; mesmo assim, em **runtime** só um caminho é carregado conforme a configuração — evitar inicializar pools/conexões dos dois.
- Documentar no README: “uma aplicação = um provider”; trocar de provider = alterar configuração e, se aplicável, dependências declaradas.

### 0.2 Autenticação e TLS no ElastiCache (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Autenticação** | **Senha** (AUTH / password para Redis; equivalente para Memcached conforme o engine). É o modelo suportado pela lib. |
| **TLS** | **Configurável** (ex.: propriedades externas / perfil de deploy). Além disso, deve ser possível **passar parâmetros de TLS no momento da criação** do cliente/cache (ex.: builder, fábrica ou objeto de configuração), para cenários programáticos ou testes sem depender só de ficheiros. |
| **IAM (Redis 7+ / IAM auth)** | **Fora de escopo** — **sem suporte**; não implementar fluxo de token IAM para ElastiCache. |

**Implicações de implementação (rascunho):**

- Modelo de configuração em **camadas**: defaults → config global (YAML/properties, se o consumidor ler) → **override** no builder/factory ao criar o `*CacheClient` / componentes.
- Para **senha**: suportar leitura a partir de variável de ambiente, property e/ou parâmetro explícito no builder (sem registar em logs).
- Para **TLS**: expor flags como `useTls`, *trust store* / *keystore* ou `SSLContext` quando fizer sentido para Lettuce/Spymemcached; documentar o mínimo necessário para ElastiCache em TLS.
- **Não** incluir dependências ou código específico para **IAM authentication** com ElastiCache.

### 0.3 Stack: core puro Java e AWS (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Escopo da biblioteca** | **Core puro**: apenas **Java** e integrações **AWS** (SDK onde aplicável, clientes Redis/Memcached para dados). **Sem** módulo opcional Spring Boot nem *starters* oficiais desta lib. |
| **Frameworks web/DI** | **Agnóstico**. Quem usa Spring, Quarkus, Micronaut, etc. **regista** fábricas e clientes expostos pela lib no seu próprio código — não faz parte do artefacto `aws-java-cache-provider`. |
| **Documentação** | Exemplos podem mostrar integração com Spring **só como ilustração** no README ou *cookbook* externo, não como código publicado no grupo Maven da lib (salvo decisão futura explícita). |

**Implicações de implementação (rascunho):**

- APIs baseadas em **interfaces**, builders e fábricas (`*CacheConfiguration`, `*CacheClientFactory`), sem anotações de framework na API pública.
- **Não** declarar `spring-boot-starter-*`, `spring-context`, etc. como dependências de compilação do `core`.
- Testes da lib: JUnit + **LocalStack** (Testcontainers) para integração AWS e ambiente local — ver [§ 1.6](#16-testes-e-ambiente-local-decidido); eventual uso de Spring **apenas** em exemplos ou repositório de *samples* separado, se existir.

### 0.4 Contrato da camada JPA plugável (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Contrato** | Uma **interface de estilo CRUD orientada à persistência JPA** (fonte de verdade). O nome final da interface fica a cargo da implementação (ex.: `JpaEntityRepository`, `SourceRepository` — evitar acoplar ao nome até o código existir). |
| **Genéricos** | **`ID`** (tipo da chave primária) e **`M`** (tipo do *model* / entidade) são **parâmetros de tipo genéricos** da interface, por exemplo `Repository<ID, M>`, para reutilizar as estratégias de cache com diferentes agregados. |
| **Pacotes** | Interfaces e tipos públicos da lib em **`…api`** (ou subpacote estável, ex. `…api.persistence`). Implementações que ligam cache ↔ repositório, *loaders*, etc., em **`…internal`** (não parte da API estável semântica). |
| **Escopo Maven (`jakarta.persistence` / Hibernate)** | **`jakarta.persistence-api`**: preferencialmente escopo **`provided`** na lib — a **aplicação consumidora** traz o provedor JPA (Hibernate, EclipseLink, …). Se algum módulo da lib precisar de tipos JPA só para adaptadores, manter **`provided`** ou isolar nesse módulo; **Hibernate** continua **não** obrigatório no `core`, apenas no consumidor ou `test`. |

**Implicações de implementação (rascunho):**

- Definir operações CRUD mínimas esperadas (ex.: `findById`, `save`, `deleteById`, eventualmente `findAll` paginado — fechar na primeira versão da interface).
- As estratégias (*cache-aside*, *read-through*, etc.) dependem desta **interface CRUD genérica**; *mocks* em testes usam `ID` e `M` concretos.
- Documentar que implementações reais usam **`EntityManager`**, repositórios ou DAOs **na aplicação**, implementando a interface genérica.

**Fase 0 (decisões de escopo) — concluída.**

---

## Fase 1 — Criação inicial do projeto (Java 25 + Maven)

### 1.1 Estrutura e build

- [ ] Criar `pom.xml` raiz (packaging `jar` ou `pom` multi-módulo: ex. `core` + adaptadores `redis` / `memcached` + `jpa` — **runtime continua com um único provider** por aplicação; multi-módulo serve organização e dependências opcionais).
- [ ] Configurar **Java 25**:
  - [ ] `maven.compiler.release` (ou `source`/`target`) = `25`.
  - [ ] `maven-compiler-plugin` com versão compatível com JDK 25.
- [ ] Adicionar **Maven Wrapper** (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`) para builds reproduzíveis.
- [ ] Definir `groupId`, `artifactId`, `version` e convenção de pacotes (ex.: `com.github.pereirrd.cache.aws` — ajustar ao grupo real).
- [ ] Configurar **quality gate** mínimo: `maven-surefire-plugin` para testes; opcionalmente `maven-failsafe-plugin` para integração.

### 1.2 Padrões de código e documentação

- [ ] `.editorconfig` / formatação alinhada ao time (Spotless, fmt-maven-plugin, ou Checkstyle — escolher uma).
- [ ] `README.md` com pré-requisitos (JDK 25, Maven), como compilar e como publicar no repositório de artefatos.
- [ ] Licença e `LICENSE` no repositório, se aplicável.

### 1.3 Dependências — AWS (SDK for Java 2.x)

Usar o **BOM** do AWS SDK v2 para alinhar versões; incluir apenas módulos necessários.

- [ ] Importar BOM: `software.amazon.awssdk:bom` (escopo `import`, tipo `pom` no `dependencyManagement`).
- [ ] **ElastiCache (control plane / operações de API):** `software.amazon.awssdk:elasticache` — útil para automação, descoberta ou tooling (não substitui cliente Redis/Memcached de dados).
- [ ] **Credenciais e região:** `software.amazon.awssdk:auth`, `software.amazon.awssdk:regions` (normalmente transitivos; declarar se quiser controle explícito).
- [ ] **STS (opcional):** `software.amazon.awssdk:sts` — assumir roles em CI ou workloads (EKS, etc.).
- [ ] **Secrets Manager (opcional):** `software.amazon.awssdk:secretsmanager` — ler senha/URL de conexão se não usar env vars.
- [ ] **CloudWatch Metrics (opcional):** `software.amazon.awssdk:cloudwatch` ou micrometer bridge — métricas de hit/miss/latência.

> **Nota:** o tráfego **de dados** para Redis/Memcached no ElastiCache usa protocolos Redis/Memcached; a AWS não substitui isso por um único “SDK de cache de dados”. O SDK AWS cobre **gestão**, **segurança** e **observabilidade** ao redor do cluster.

### 1.4 Dependências — clientes de protocolo (dados)

- [ ] **Redis:** `io.lettuce:lettuce-core` (recomendado para async/reactive) *ou* `redis.clients:jedis` — escolha uma base para a implementação.
- [ ] **Memcached:** `net.spy:spymemcached` *ou* alternativa mantida alinhada ao protocolo — validar compatibilidade com o engine do ElastiCache.

### 1.5 Dependências — JPA / persistência (plugável)

- [ ] API JPA: `jakarta.persistence:jakarta.persistence-api` — preferir **`provided`** para a app consumidora trazer o provedor; usar `compile` apenas se um módulo da lib expuser tipos JPA em API pública (alinhado ao [§ 0.4](#04-contrato-da-camada-jpa-plugavel-decidido)).
- [ ] **Não** embutir Hibernate como obrigatório no `core` se a ideia é o usuário trazer o provedor JPA; preferir `provided` para `org.hibernate.orm:hibernate-core` apenas em módulos de teste ou módulo `jpa-adapter`.
- [ ] **Sem** `spring-boot-starter-data-jpa` na lib; o consumidor traz JPA/Hibernate/Spring Data na aplicação. Testes do módulo JPA da lib: apenas `jakarta.persistence-api` + provedor de teste (ex.: Hibernate + H2) com escopo `test`, se necessário.

### 1.6 Testes e ambiente local (decidido)

| Aspecto | Decisão |
|--------|---------|
| **Stack local e de testes** | **LocalStack** para **testes de integração** e para **ambiente local** de desenvolvimento (emulador AWS em Docker). |
| **Objetivo** | Alinhar testes e `docker-compose` (ou equivalente) com o mesmo padrão de endpoints e credenciais que a AWS real, usando o **AWS SDK v2** com *endpoint* apontando para o LocalStack. |

**Checklist de implementação**

- [ ] Testcontainers: `org.testcontainers:testcontainers` + **`org.testcontainers:localstack`** para subir o LocalStack nos testes (JUnit 5).
- [ ] Configurar clientes AWS nos testes com **endpoint override** (ex.: `http://localhost:<porta>` exposto pelo container LocalStack) e região fictícia coerente com a [documentação LocalStack](https://docs.localstack.cloud/).
- [ ] **Ambiente local:** `docker-compose` (ou *compose plugin*) com imagem **LocalStack** para desenvolvimento manual; documentar no `README` variáveis (`AWS_ACCESS_KEY_ID=test`, `AWS_SECRET_ACCESS_KEY=test`, `AWS_DEFAULT_REGION`, URL do endpoint).
- [ ] Validar quais serviços AWS usados pela lib (ex.: ElastiCache API *control plane*, Secrets Manager) estão disponíveis na edição do LocalStack em uso (Community vs Pro) e ajustar testes em conformidade.
- [ ] JUnit 5 (`org.junit.jupiter:junit-jupiter`).
- [ ] Assertions: AssertJ (`org.assertj:assertj-core`) — opcional mas recomendado.

> **Nota:** chamadas de **dados** ao protocolo Redis/Memcached (Lettuce/Spymemcached) podem usar o endpoint real do cluster de teste ou o que o LocalStack/expansões expuserem; manter os testes de protocolo alinhados ao que for suportado no stack escolhido.

---

## Fase 2 — Núcleo da API da biblioteca (comum a todas as estratégias)

- [ ] Definir interfaces públicas: fábrica de conexão (Redis vs Memcached), configuração (endpoints, TLS, timeouts, pool).
- [ ] Definir modelo de chave/valor (serialização: JSON, Kryo, ou bytes + `Charset`) e limites de tamanho alinhados ao engine.
- [ ] Definir exceções hierárquicas (`CacheException`, timeouts, indisponibilidade).
- [ ] Métricas e hooks opcionais (listeners de hit/miss, latência).
- [ ] Documentar semântica de **TTL** por estratégia (onde se aplica).

---

## Fase 3 — Camada plugável “fonte de verdade” (JPA)

- [ ] Formalizar a **interface CRUD genérica** `<ID, M>` (ver [§ 0.4](#04-contrato-da-camada-jpa-plugavel-decidido)): assinaturas, exceções e contrato transacional esperado, **sem** expor `EntityManager` na API pública da lib.
- [ ] Documentar como o **consumidor** implementa a interface com JPA (`EntityManager`, DAO, etc.); *Spring Data* fica opcional e fora do artefacto principal.
- [ ] Garantir transações onde write-through / write-behind exigem consistência (contrato do repositório genérico: participação na transação JPA; exemplos na app podem usar `@Transactional`, não na API pública da lib).
- [ ] Testes de integração com banco em memória (H2) ou Testcontainers (PostgreSQL/MySQL) **apenas** no módulo JPA, para não poluir o `core`.

---

## Fase 4 — Estratégia: Cache-aside

- [ ] Implementar fluxo: `get` → hit/miss → em miss, delegar ao **repositório de origem (interface CRUD `<ID, M>`)** → `set` no cache.
- [ ] Criar **anotações** Java (`@Target`, `@Retention` adequados) para configurar *cache-aside* de forma declarativa (ex.: chave, TTL, identificador lógico do cache); documentar o contrato. O **processamento** em runtime (interceptor, *AspectJ*, reflexão) fica opcional ou à cargo da aplicação, mantendo o [§ 0.3](#03-stack-core-puro-java-e-aws-decidido) (sem Spring na lib).
- [ ] API para invalidação/atualização explícita após escritas na aplicação (documentar contrato).
- [ ] Testes unitários com mock do cliente Redis/Memcached e mock do **repositório genérico**.
- [ ] Testes de integração com **LocalStack** ([§ 1.6](#16-testes-e-ambiente-local-decidido)).

---

## Fase 5 — Estratégia: Read-through

- [ ] Implementar leitura **somente** via API de cache; registrar **loader**/`CacheLoader` chamado pelo provedor em *miss* (padrão semelhante a `LoadingCache` conceitualmente).
- [ ] Garantir que apenas uma carga por chave ocorra sob concorrência (*single loader* / lock por chave — conforme suporte do cliente).
- [ ] Testes de concorrência (vários threads, mesma chave, uma ida à origem).
- [ ] Testes de integração com **LocalStack** ([§ 1.6](#16-testes-e-ambiente-local-decidido)).

---

## Fase 6 — Estratégia: Write-through

- [ ] Implementar escrita: atualizar **origem (JPA / interface CRUD)** e **cache** na mesma operação lógica; definir ordem (ex.: BD primeiro, depois cache — ou o inverso conforme risco de inconsistência aceito).
- [ ] Tratamento de falha parcial (compensação, retry idempotente, ou invalidação de cache).
- [ ] Testes com repositório fake + ambiente **LocalStack** / clientes de cache conforme [§ 1.6](#16-testes-e-ambiente-local-decidido); cenários de falha (origem falha, cache falha).

---

## Fase 7 — Estratégia: Write-behind / Write-back

- [ ] Fila interna durável ou em memória com política clara: `BlockingQueue`, executor, batch de escrita na origem.
- [ ] Persistência da fila (opcional): se aceitar perda em crash, documentar; se não, considerar fila externa ou journal leve (escopo maior).
- [ ] Escritas idempotentes na origem (documentar requisitos para a **implementação JPA** da interface CRUD).
- [ ] *Backpressure*: limites de fila, descarte vs bloqueio, métricas.
- [ ] Testes de carga leve e testes de shutdown ordenado (flush antes de encerrar).
- [ ] Testes de integração com **LocalStack** ([§ 1.6](#16-testes-e-ambiente-local-decidido)).

---

## Fase 8 — Homogeneização e release

- [ ] Exemplos mínimos no `README` ou módulo `examples`: um snippet por estratégia.
- [ ] Pipeline CI: `mvn verify` em JDK 25, cache de dependências Maven.
- [ ] Versionamento semântico e publicação (Maven Central / GitHub Packages) — definir `distributionManagement` e secrets.
- [ ] Revisão de segurança: dependências sem CVEs conhecidos (OWASP Dependency-Check ou Dependabot).

---

## Referência rápida — artefatos Maven citados

| Finalidade | Coordenadas (exemplo) |
|------------|------------------------|
| BOM AWS SDK v2 | `software.amazon.awssdk:bom` |
| API ElastiCache (control plane) | `software.amazon.awssdk:elasticache` |
| Cliente Redis (dados) | `io.lettuce:lettuce-core` ou `redis.clients:jedis` |
| Cliente Memcached (dados) | `net.spy:spymemcached` (validar versão) |
| JPA API | `jakarta.persistence:jakarta.persistence-api` |
| Testes | `org.junit.jupiter:junit-jupiter`, `org.testcontainers:testcontainers`, `org.testcontainers:localstack` |

*(Ajuste versões no `pom` conforme o BOM AWS e compatibilidade no momento do desenvolvimento.)*

---

*Atualize este checklist quando módulos, nomes de pacotes ou escolhas de cliente Redis/Memcached forem fixados.*
