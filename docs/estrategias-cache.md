# Base de conhecimento: padrões de cache (leitura e escrita)

Este documento concentra-se em **quatro padrões** de integração entre aplicação, cache e fonte de verdade. Estão **ordenados da menor para a maior complexidade** (controle e código na aplicação → responsabilidade no provedor de cache; leitura → escrita síncrona → escrita assíncrona com durabilidade).

---

## 1. Cache-aside (*lazy loading*)

**Definição:** a aplicação consulta o cache primeiro. Em *miss*, vai à origem (banco, serviço, API), preenche o cache e devolve o resultado. Quem orquestra o fluxo é sempre o código da aplicação.

**Fluxo típico (leitura):**

1. `get(chave)` no cache  
2. Se *hit* → retorna o valor  
3. Se *miss* → lê na origem → `set(chave, valor)` → retorna  

**Escritas:** em geral a app atualiza a origem e **invalida** ou **atualiza** a entrada no cache explicitamente (não há contrato único imposto pelo padrão).

**Vantagens:** flexível; funciona com qualquer backend; a origem não precisa conhecer o cache.

**Cuidados:** invalidação e consistência são responsabilidade da aplicação; muitos *miss* simultâneos na mesma chave podem sobrecarregar a origen (*cache stampede*) se não houver proteção adicional.

**Complexidade:** **baixa a média** — lógica explícita na app, mas bem entendida e amplamente adotada.

---

## 2. Read-through

**Definição:** o cliente da aplicação (ou a própria app, como “cliente” do cache) **só interage com o cache** para leitura. Em *miss*, **o provedor de cache ou uma camada intermediária** carrega da origem, armazena no cache e devolve o valor — o fluxo “ir buscar na origem” não fica espalhado no código de negócio.

**Fluxo típico:** pedido de leitura → cache; *miss* tratado **dentro** do stack de cache (loader, *cache store* plugável, etc.).

**Diferença em relação ao cache-aside:** no cache-aside a aplicação implementa o ramo *miss*; no read-through essa responsabilidade é **centralizada** no componente de cache.

**Vantagens:** menos duplicação de código de leitura; política de carga e TTL podem ficar num só lugar.

**Cuidados:** é preciso que o produto ou biblioteca suporte *loading* configurável; invalidação ainda precisa ser definida (TTL, eventos, etc.).

**Complexidade:** **média** — depende de integração com o provedor ou abstração de *cache loader*.

---

## 3. Write-through

**Definição:** em cada escrita, os dados são gravados **no cache e na fonte de verdade** de forma **síncrona** (ou de modo que a operação só conclui quando ambos os lados foram tratados segundo a regra de negócio). Após o *commit* lógico, leituras pelo cache veem o valor atualizado.

**Fluxo típico:** `write` → persistir na origem **e** atualizar (ou substituir) a entrada no cache na mesma operação coordenada.

**Vantagens:** forte alinhamento entre cache e origem logo após a escrita; leituras subsequentes rápidas e consistentes com o que foi gravado.

**Cuidados:** latência de escrita tende a ser a soma (ou o máximo) dos dois passos; falhas parciais (origem ok, cache falhou, ou o inverso) exigem estratégia explícita de compensação ou retry.

**Complexidade:** **média a alta** no desenho de erros e ordem de operações; o conceito em si é direto.

---

## 4. Write-behind / write-back

**Definição:** a escrita é **aceita primeiro no cache** (resposta rápida ao chamador) e a **persistência na origem ocorre depois**, de forma **assíncrona** (fila, batch, intervalo de flush).

**Fluxo típico:** `write` → atualizar cache → confirmar ao cliente → em background, drenar para a origem.

**Vantagens:** baixa latência percebida e alto throughput de escrita quando a origem é lenta ou distante.

**Cuidados:** risco de **perda ou divergência** se o processo cair antes do flush; necessidade de **ordenação**, **idempotência** nos writes na origem, recuperação após falha, limites de fila e monitoramento; eventual consistência entre cache e armazenamento durável.

**Complexidade:** **alta** — o padrão empurra complexidade para filas, durabilidade do próprio cache e cenários de falha.

---

## Resumo comparativo

| Padrão        | Quem lê a origem em *miss*     | Momento da persistência na origem | Foco principal        |
|---------------|--------------------------------|-------------------------------------|------------------------|
| Cache-aside   | Aplicação                      | Fora do padrão (app decide)         | Controle e simplicidade |
| Read-through  | Provedor / camada de cache     | Leituras: sob *miss*                | Centralizar leituras   |
| Write-through | Coordenado com o cache         | Síncrono com a escrita              | Consistência pós-write |
| Write-behind  | (Leituras pelo cache)          | Assíncrono após atualizar cache     | Latência de escrita    |

---

## Como escolher (orientação rápida)

- **Cache-aside:** padrão padrão quando você quer controle total e independência da origem.  
- **Read-through:** quando há suporte de produto/biblioteca e você quer unificar o carregamento em *miss*.  
- **Write-through:** quando leituras após escrita precisam ver sempre o valor persistido sem depender de outro fluxo.  
- **Write-behind:** quando a latência de escrita na origem é gargalo e você aceita trabalhar duro em filas, idempotência e recuperação.

---

*Documento interno de referência — atualize conforme as decisões de arquitetura do projeto.*
