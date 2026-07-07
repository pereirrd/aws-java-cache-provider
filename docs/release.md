# Release e versionamento

Este documento descreve a política de versões da biblioteca **aws-java-cache-provider** e o que falta para publicar artefactos Maven num repositório remoto.

## Versionamento semântico

Seguimos [Semantic Versioning 2.0.0](https://semver.org/) (`MAJOR.MINOR.PATCH`):

| Segmento | Quando incrementar |
|----------|-------------------|
| **MAJOR** | Quebra de compatibilidade na API pública (interfaces, contratos SPI, comportamento documentado). |
| **MINOR** | Funcionalidade nova compatível com versões anteriores. |
| **PATCH** | Correções de bugs compatíveis com versões anteriores. |

### Estado actual

- Versão no POM pai: **`0.1.0-SNAPSHOT`**
- O sufixo `-SNAPSHOT` indica desenvolvimento activo; builds locais e CI produzem artefactos não imutáveis.
- Quando a implementação estiver estável para consumo externo, a primeira release estável será **`1.0.0`** (removendo `-SNAPSHOT`).

### Convenções internas

- Trabalho em curso mergeia para ramos de feature; o POM permanece em `-SNAPSHOT` até à decisão de *tag* de release.
- *Tags* Git (quando existirem) devem alinhar com a versão Maven exacta, por exemplo `v1.0.0`.
- Módulos filhos herdam a versão do POM pai — não versionar módulos independentemente.

## Publicação de artefactos

> **Decisão do projecto:** **não há publicação configurada** neste repositório. Não existe `distributionManagement`, *workflow* de *deploy* Maven, nem credenciais de repositório remoto.

Os consumidores actuais devem:

1. Clonar o repositório e executar `mvn clean install`, ou
2. Referenciar o artefacto via dependência Maven local após `install`.

## O que seria necessário para Maven Central (referência futura)

Quando o projecto decidir publicar, seria necessário (sem alterações neste documento ao POM):

1. **Contas e metadados**
   - Conta [Sonatype Central Portal](https://central.sonatype.org/) (ou sucessor OSSRH).
   - `groupId` verificado (`io.github.pereirrd` ou domínio próprio).
   - POM com `name`, `description`, `url`, `licenses`, `developers`, `scm`.

2. **Assinatura e integridade**
   - GPG para assinar artefactos (`maven-gpg-plugin`).
   - *Checksums* e *signatures* exigidos pelo Central.

3. **Build reprodutível**
   - `-Prelease` ou perfil equivalente com `maven-source-plugin`, `maven-javadoc-plugin`, `central-publishing-maven-plugin` (ou `nexus-staging-maven-plugin` legado).

4. **CI**
   - *Pipeline* que corre `mvn clean verify`, depois `deploy` com segredos (`OSSRH_USERNAME`, `OSSRH_PASSWORD`, chave GPG) — **fora do scope actual**.

5. **Alternativa:** GitHub Packages ou repositório privado corporativo, com `distributionManagement` apontando ao URL do registry escolhido.

Nenhum destes passos está implementado; a Fase 8 do [`checklist.md`](checklist.md) marca publicação e OWASP/Dependabot como **fora de scope / adiado**.

## Checklist antes de `1.0.0`

- [ ] `mvn clean verify` verde (unitários + Spotless).
- [ ] Testes de integração documentados passam com stack local ([`integration-tests.md`](integration-tests.md)).
- [ ] README com coordenadas Maven finais e matriz de módulos.
- [ ] CHANGELOG ou notas de release na *tag*.
- [ ] Remover `-SNAPSHOT` e publicar (quando infra de *deploy* existir).
