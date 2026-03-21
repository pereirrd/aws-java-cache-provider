# aws-java-cache-provider

Biblioteca Java com estratégias de cache (*cache-aside*, *read-through*, *write-through*, *write-behind*) sobre **Amazon ElastiCache** (Redis ou Memcached) e persistência JPA plugável.

## Pré-requisitos

- **JDK 25** (definido em `maven.compiler.release` no POM raiz).
- **Apache Maven** instalado e `JAVA_HOME` apontando para o JDK 25.

## Compilar

Na raiz do repositório:

```bash
mvn clean verify
```

A fase `verify` inclui formatação (Spotless). Para aplicar o formato sem falhar o build:

```bash
mvn spotless:apply
```

## Módulos (coordenadas Maven)

| Módulo | `artifactId` |
|--------|----------------|
| Núcleo partilhado | `aws-java-cache-provider-core` |
| SPI JPA (opcional) | `aws-java-cache-provider-jpa-api` |
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
