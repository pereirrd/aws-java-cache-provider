# Testes de integração

Este documento descreve como executar os testes de integração da biblioteca contra **LocalStack**
(*control plane*) e **Redis** (*data plane*). Para arquitetura da stack e matriz Community vs Pro,
ver [`localstack.md`](localstack.md).

## Visão geral

| Comando | Profile Maven | Requer Docker API | Requer stack no ar |
|---------|---------------|-------------------|--------------------|
| `mvn clean verify` | *(nenhum)* | Não | Não |
| `mvn clean verify -Pintegration` | `integration` | **Sim** (Testcontainers) | Não — sobe containers |
| `mvn clean verify -Pintegration-compose` | `integration-compose` | Não | **Sim** (`docker compose up`) |

- **`mvn clean verify`** — unitários + Spotless; **nunca** depende de Docker.
- **`-Pintegration`** — Testcontainers arranca LocalStack e Redis automaticamente. Exige permissão
  para falar com `/var/run/docker.sock` (utilizador no grupo `docker` ou Docker Desktop com WSL).
- **`-Pintegration-compose`** — liga-se a `localhost:4566` (LocalStack) e `localhost:6379` (Redis)
  conforme o `.env`. **Não** precisa de Docker API; ideal para agentes de IA, CI sem socket, ou
  quando o processo Maven não está no grupo `docker`.

## Classes de teste

| Profile | Padrão Failsafe | Módulo | Conteúdo |
|---------|-----------------|--------|----------|
| `integration` | `*IT.java` (exceto `*ComposeIT`) | `…-core`, `…-cache-aside` | Testcontainers sobe containers |
| `integration-compose` | `*ComposeIT.java` | `…-core`, `…-cache-aside` | Stack externa via `.env` |

**Core (`integration-compose`):**

- `LocalStackAwsSdkComposeIT` — STS, Secrets Manager (secret de bootstrap), CloudWatch
- `RedisCacheProviderComposeIT` — put/get, remove, TTL

**Cache-aside (`integration-compose`):**

- `CacheAsideServiceComposeIT` — miss, hit, evict com Redis real

**Read-through (`integration-compose`):**

- `ReadThroughServiceComposeIT` — miss, hit, evict com Redis real

**Write-through (`integration-compose`):**

- `WriteThroughServiceComposeIT` — save origem→cache, hit, delete origem→invalidação

**Write-behind (`integration-compose`):**

- `WriteBehindServiceComposeIT` — save cache→flush origem, hit, delete cache→flush origem

Total: **18 testes** de integração compose (6 no core + 3 por módulo de estratégia).

---

## Configuração (LocalStack + Redis)

### 1. Ficheiro de ambiente

```bash
cp .env.example .env
```

Edite `.env` conforme necessário. Variáveis essenciais:

| Variável | Valor típico | Função |
|----------|--------------|--------|
| `AWS_JAVA_CACHE_LOCALSTACK_ENABLED` | `true` | Ativa endpoint override para LocalStack |
| `AWS_JAVA_CACHE_LOCALSTACK_HOST` | `localhost` | Host do gateway LocalStack |
| `AWS_JAVA_CACHE_LOCALSTACK_PORT` | `4566` | Porta do gateway |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | `test` / `test` | Credenciais aceites pelo LocalStack |
| `AWS_DEFAULT_REGION` | `us-east-1` | Região emulada |
| `AWS_JAVA_CACHE_REDIS_HOST` | `localhost` | Host Redis (data plane) |
| `AWS_JAVA_CACHE_REDIS_PORT` | `6379` | Porta Redis |
| `LOCALSTACK_AUTH_TOKEN` | *(token)* | Obrigatório em imagens LocalStack recentes (março 2026+) |

Obtenha o token em [app.localstack.cloud](https://app.localstack.cloud). Sem token, o container
LocalStack pode falhar ao arrancar (compose e Testcontainers).

O `docker-compose.yml` repassa `LOCALSTACK_AUTH_TOKEN` do `.env` para o serviço `localstack`.

### 2. Bootstrap automático

Ao subir o LocalStack, o script `localstack/init/ready.d/01-bootstrap.sh` cria o secret:

- **Nome:** `aws-java-cache/local/redis-password`
- **Valor:** `local-dev-password`

Os testes compose validam a leitura deste secret via AWS SDK.

---

## Subir a stack Docker (passo manual)

Necessário para **`-Pintegration-compose`** e para desenvolvimento manual. Também é o fluxo a seguir
quando um **agente de IA ou CI não tem permissão** para usar o Docker API.

### Pré-requisitos Docker

- Docker Desktop (WSL) ou Docker Engine no Linux
- Utilizador no grupo `docker` (Linux/WSL):

```bash
sudo usermod -aG docker $USER
# Reinicie a sessão WSL ou faça logout/login para o grupo ter efeito:
#   PowerShell: wsl --shutdown
#   ou abra um novo terminal
groups   # deve listar "docker"
docker ps
```

Se `docker ps` devolver `permission denied`, o Maven com Testcontainers (`-Pintegration`) **não**
consegue arrancar containers — use `-Pintegration-compose` depois de `docker compose up -d`.

### Comandos

Na raiz do repositório:

```bash
docker compose up -d
```

Com Memcached (opcional):

```bash
docker compose --profile memcached up -d
```

Exporte o `.env` no mesmo shell onde corre o Maven:

```bash
set -a && source .env && set +a
```

### Validar antes dos testes

```bash
# LocalStack (esperado: HTTP 200)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:4566/_localstack/health

# Redis (esperado: PONG)
docker exec aws-java-cache-redis redis-cli ping

# Secret de bootstrap
docker exec aws-java-cache-localstack awslocal secretsmanager get-secret-value \
  --secret-id aws-java-cache/local/redis-password
```

---

## Executar os testes

### Fluxo recomendado (humano ou IA sem Docker API)

O **utilizador** sobe a stack; o **Maven/agente** só precisa de rede para `localhost`:

```bash
# 1. Utilizador: subir stack (uma vez por sessão)
docker compose up -d

# 2. Carregar variáveis
set -a && source .env && set +a

# 3. Integração compose (Failsafe, classes *ComposeIT.java)
mvn clean verify -Pintegration-compose
```

Com `mise` num shell não-login:

```bash
eval "$(mise activate bash)"
set -a && source .env && set +a
mvn clean verify -Pintegration-compose
```

### Fluxo Testcontainers (Docker API disponível)

```bash
set -a && source .env && set +a   # LOCALSTACK_AUTH_TOKEN para o container LocalStack
mvn clean verify -Pintegration
```

Testcontainers usa a imagem `localstack/localstack:4.4` e `redis:7.4-alpine`. Se Docker não estiver
acessível, os testes `*IT.java` são **skipped** (não falham o build).

### Apenas módulos com integração

```bash
mvn verify -Pintegration-compose -pl aws-java-cache-provider-core,aws-java-cache-provider-cache-aside,aws-java-cache-provider-read-through,aws-java-cache-provider-write-through,aws-java-cache-provider-write-behind
```

---

## Guia para agentes de IA (Cursor Cloud / sandbox)

Ambientes automatizados frequentemente **não** têm acesso a `/var/run/docker.sock`, mesmo com Docker
instalado no WSL do utilizador.

| Situação | O que fazer |
|----------|-------------|
| Agente não corre `docker compose` | Pedir ao **utilizador** que execute `docker compose up -d` no terminal local |
| `docker ps` → permission denied no agente | Utilizador adiciona-se ao grupo `docker` e reinicia WSL; ou usar sempre `-Pintegration-compose` |
| Testes skipped (0 executados) | Stack não está no ar ou `.env` não foi carregado antes do `mvn` |
| LocalStack OK, Redis skipped | Aguardar Redis ficar healthy; confirmar `AWS_JAVA_CACHE_REDIS_*` no ambiente do Maven |

**Checklist para o agente antes de `-Pintegration-compose`:**

1. Confirmar LocalStack: `curl -s -o /dev/null -w "%{http_code}" http://localhost:4566/_localstack/health` → `200`
2. Confirmar Redis: `docker exec aws-java-cache-redis redis-cli ping` → `PONG` *(se o agente tiver Docker)* ou `nc`/cliente TCP na porta 6379
3. `set -a && source .env && set +a` no mesmo comando que invoca o Maven
4. Correr `mvn verify -Pintegration-compose`

**Checklist para o utilizador (quando o agente pedir):**

```bash
cd /caminho/para/aws-java-cache-provider
cp .env.example .env    # se ainda não existir; preencher LOCALSTACK_AUTH_TOKEN
docker compose up -d
docker compose ps       # localstack + redis "running"/"healthy"
```

Depois disso, o agente pode executar os testes compose sem tocar no Docker.

---

## Resolução de problemas

| Sintoma | Causa provável | Solução |
|---------|----------------|---------|
| `Tests run: N, Skipped: N` (integração) | Docker indisponível no processo Maven | `-Pintegration-compose` + stack no ar |
| `Tests run: 0` numa classe compose | `@BeforeAll` / stack indisponível no arranque | Garantir health checks OK; repetir após `docker compose up` |
| LocalStack container exit imediato | Falta `LOCALSTACK_AUTH_TOKEN` | Definir no `.env`; `docker compose up -d` de novo |
| Falhas em `CacheAsideServiceComposeIT` | Dados antigos no Redis | Os testes compose fazem `flush()` — reiniciar Redis se persistir: `docker compose restart redis` |
| `permission denied` em docker.sock | Sessão sem grupo `docker` | `usermod -aG docker` + reiniciar WSL |
| Secret não encontrado | Bootstrap não correu | `docker compose down -v && docker compose up -d` ou criar secret manualmente (ver `localstack.md`) |

---

## Parar a stack

```bash
docker compose down      # para containers
docker compose down -v   # remove volumes (estado LocalStack/Redis)
```

---

## Referências

- [`localstack.md`](localstack.md) — arquitetura, matriz de serviços, validação manual
- [`checklist.md`](checklist.md) — fases do projeto (§ 0.6 testes)
- [`../.env.example`](../.env.example) — template de variáveis
- [`../docker-compose.yml`](../docker-compose.yml) — serviços LocalStack + Redis
