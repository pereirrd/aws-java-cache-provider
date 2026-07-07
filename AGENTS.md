# AGENTS.md

## Cursor Cloud specific instructions

This repository is a **pure Java multi-module Maven library** (no runnable server/app). It provides
cache strategies (cache-aside, read-through, write-through, write-behind) over Amazon ElastiCache.

### Toolchain
- Requires **JDK 25** and **Maven** (see `mise.toml`: `java = corretto-25`, `maven = latest`).
- Toolchain is managed by [`mise`](https://mise.jdx.dev). The startup update script installs `mise`
  and runs `mise install` (reads `mise.toml`). `mise` is activated in `~/.bashrc`, so a normal login
  shell already has `java` and `mvn` on `PATH`. In non-login/non-interactive shells, prefix commands
  with `mise exec --` (e.g. `mise exec -- mvn ...`) or run `eval "$(mise activate bash)"` first.

### Build / test / lint (standard commands from README)
- Full build + tests + lint: `mvn clean verify` (lint = Spotless `check`, bound to the `verify` phase).
- Build + tests only (no lint): `mvn clean package`.
- Lint only: `mvn spotless:check`; auto-fix formatting with `mvn spotless:apply`.
- Tests use JUnit 5 + AssertJ with in-memory stubs — **no Redis/Memcached/AWS/LocalStack needed**.

### Non-obvious notes
- Spotless uses palantir-java-format and enforces trailing-whitespace/import rules; a formatting
  violation fails `mvn verify` even when compilation and tests pass. Run `mvn spotless:apply` before
  committing Java changes.
- Being a library, there is no `run` target. To exercise the public API end-to-end, write a small
  consumer that implements `CacheProvider` + `BackingRepository` and drives `CacheAsideService`
  (see README for the cache-aside snippet), or rely on the module unit tests.
