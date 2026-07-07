# TTL por estratégia

Este documento descreve como o **time-to-live (TTL)** é aplicado em cada estratégia e no `CacheProvider` de baixo nível.

## `CacheProvider` (core)

| Método | TTL |
|--------|-----|
| `put(key, value)` | Sem expiração explícita (comportamento depende do engine) |
| `put(key, value, ttl)` | Expira após `ttl` quando positivo; `null`, zero ou negativo equivale a `put(key, value)` |

**Redis (Lettuce):** `SET` com `EX` quando `ttl` é positivo.

**Memcached (Spymemcached):** `set` com expiração em segundos (arredondamento para cima a partir de `Duration`).

## Estratégias

| Estratégia | Quem define o TTL | Quando é aplicado |
|------------|-------------------|-------------------|
| **Cache-aside** | Construtor de `CacheAsideService` (`entryTtl`) | `get` em *miss* (`put` com TTL); `putCached` após escrita na origem |
| **Read-through** | Construtor de `ReadThroughService` | `get` em *miss* ao popular o cache |
| **Write-through** | Construtor de `WriteThroughService` | `get` em *miss*; `save` após persistir na origem |
| **Write-behind** | Construtor de `WriteBehindService` | `get` em *miss*; `save` ao atualizar o cache antes da fila |

## Notas

- O TTL é **por serviço/instância**, não por chave individual na API programática atual.
- Escritas na origem feitas **fora** do serviço de cache (cache-aside, read-through) não renovam TTL automaticamente — use `evict` ou `putCached` conforme a estratégia.
- Em *write-behind*, o cache pode ficar à frente da origem; o TTL limita apenas a vida da entrada no cache, não a durabilidade na origem.
- Limites máximos de TTL dependem do engine (ex.: Memcached tem teto de expiração relativa); documente no consumidor se usar TTLs longos.
