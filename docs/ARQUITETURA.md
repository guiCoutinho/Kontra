# Arquitetura — Kontra

> Mantido pelo agente `arquiteto`. Última revisão: 2026-10-06 (Marco 0 aprovado; convenção de idioma — ADR-013).
> Estado atual: **nada implementado** — o repositório contém só `.claude/`, `CLAUDE.md` e `docs/`. Este documento descreve a arquitetura-alvo do Marco 0 e as convenções que valem daí em diante.

## Visão geral

Aplicação web em monorepo, com três peças em execução e um contrato compartilhado:

```
                    ┌──────────────────────────── docker compose ─────────────────────────────┐
Navegador ──HTTP──► │ frontend (nginx :8080 → host :3000)                                     │
                    │   ├─ /            → SPA React (arquivos estáticos, fallback index.html) │
                    │   └─ /api/**      → proxy ──► backend (Spring Boot :8080) ──► banco (PG 18)│
                    └─────────────────────────────────────────────────────────────────────────┘

Dev local:  Navegador ──► Vite :5173 ──(proxy /api)──► backend :8080 (mvnw) ──► banco do compose :5432
```

- **Mesma origem sempre**: o navegador só fala com a origem do frontend; `/api/**` é repassado ao backend (proxy do Vite em dev, nginx no compose). Não há CORS (ADR-005).
- **Contrato**: o backend gera o OpenAPI (springdoc). Um snapshot versionado (`backend/openapi/openapi.json`) é a fonte para o cliente TypeScript gerado pelo orval em `frontend/src/api/generated/` (ADR-003, ADR-004).
- **Banco**: PostgreSQL 18; esquema 100% controlado por Flyway (`ddl-auto=validate`).
- **Segurança**: Spring Security ativo, sem mecanismo de autenticação no Marco 0; tudo é negado por padrão (401), exceto a verificação de saúde e a documentação da API (ADR-007).
- **Marco 0 não tem domínio de negócio**: a única funcionalidade é `GET /api/saude` (verificação de saúde), exibida pela tela inicial.

### Portas

| Serviço | Dev local | docker compose (host → container) |
|---|---|---|
| Frontend | `5173` (Vite) | `127.0.0.1:${PORTA_FRONTEND:-3000}` → `8080` (nginx não root) |
| Backend | `8080` | `127.0.0.1:${PORTA_BACKEND:-8080}` → `8080` |
| PostgreSQL | `5432` (serviço `banco` do compose) | `127.0.0.1:${PORTA_POSTGRES:-5432}` → `5432` |

No Marco 0, **todas** as portas do compose (inclusive a do frontend) são publicadas só no loopback (`127.0.0.1`): ficam acessíveis em `localhost`, mas não pela rede local. Expor na rede é decisão de um marco futuro (com autenticação). O compose não usa `restart` policy no Marco 0.

Swagger UI fica no backend (`http://localhost:8080/swagger-ui.html`); o nginx do frontend não repassa `/swagger-ui` nem `/v3/api-docs`.

### Convenções gerais

- **Idioma**: tudo em português, sem acento nos identificadores — regra completa e exemplos no **ADR-013**.
- Rotas REST sempre sob `/api/<recurso>` (substantivo no plural para coleções, a partir do Marco 1 — ex.: `/api/contratos`), JSON em `camelCase`, datas em ISO-8601 UTC (`format: date-time`).
- Todo campo de resposta que nunca é nulo é marcado como obrigatório no schema (`@Schema(requiredMode = REQUIRED)`), para o cliente gerado não ter campos opcionais falsos.
- Toda operação tem `operationId` explícito, no formato verbo no infinitivo + recurso (vira o nome do hook gerado: `obterSaude` → `useObterSaude`), e `@Tag` explícita com o nome do módulo (vira a pasta do cliente gerado).

## Backend — pacotes

Projeto Maven em `backend/` — `groupId` `br.com.kontra`, `artifactId` `kontra-backend`, pacote raiz `br.com.kontra` (confirmado pelo usuário em 2026-10-06; demais artefatos: `kontra-frontend`, `kontra-e2e`).

```
backend/
├── pom.xml, mvnw, mvnw.cmd, .mvn/
├── openapi/openapi.json                     # snapshot do contrato (gerado pelo SnapshotOpenApiIT; versionado)
└── src/
    ├── main/java/br/com/kontra/
    │   ├── KontraApplication.java
    │   ├── config/                          # SegurancaConfig, OpenApiConfig
    │   ├── comum/erro/                      # TratadorGlobalDeExcecoes, ExcecaoApi, CodigoErro,
    │   │                                    # ProblemaResponse, ErroDeCampo (schemas do contrato),
    │   │                                    # FabricaDeProblemas, TratadorNaoAutenticado (401),
    │   │                                    # TratadorAcessoNegado (403), ErroPadraoController (/error),
    │   │                                    # TratadorRequisicaoRejeitada (firewall do Security)
    │   ├── saude/
    │   │   ├── controller/SaudeController
    │   │   ├── service/SaudeService
    │   │   └── dto/SaudeResponse, StatusComponente
    │   └── <modulo>/                        # a partir do Marco 1 (ex.: contrato/)
    │       ├── controller/  service/  repository/  domain/  dto/
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/V<n>__<descricao>.sql
    └── test/java/br/com/kontra/
        ├── suporte/TestcontainersConfig.java   # PostgreSQL 18 via @ServiceConnection, reutilizado por todos os *IT
        ├── suporte/TesteDeIntegracao.java      # meta-anotação obrigatória de todo *IT (ADR-011)
        ├── config/SnapshotOpenApiIT.java
        └── <espelha o pacote testado>/  *Test.java (unit/slice)  *IT.java (integração)
```

Regras:
- Camadas: `controller` (HTTP, validação de entrada, mapeamento DTO) → `service` (regra de negócio, `@Transactional`) → `repository` (Spring Data). Controllers nunca acessam repositories.
- `domain/` contém entidades JPA (nome em português, sem sufixo: `Contrato`); elas nunca saem do service — controllers recebem/devolvem só DTOs (`record`s em `dto/`, com sufixo `Request` para entrada e `Response` para saída).
- Exceções de negócio estendem `comum.erro.ExcecaoApi` (carrega `HttpStatus` + `CodigoErro` + mensagem em pt-BR); só aceita status de erro (4xx/5xx) — outro status é rejeitado na construção. Nunca se lança `ResponseStatusException` diretamente.
- **DTOs do contrato e Jackson**: o springdoc gera os schemas com o Jackson 2 do swagger-core, que **não** enxerga as anotações de databind do Jackson 3 (`tools.jackson.databind.annotation.*`, ex.: `@JsonSerialize`, `@JsonNaming`) nem a configuração `spring.jackson.*`. Nos DTOs do contrato, usar só `com.fasterxml.jackson.annotation.*` (compartilhadas pelos dois, ex.: `@JsonProperty`, `@JsonInclude`) e não configurar nada em `spring.jackson.*` que mude o formato do JSON (nomes, inclusão, datas). Um teste compara o JSON real com o schema publicado (T6 do Marco 0).
- Configuração lida de `application.yml` com placeholders de variáveis de ambiente (`${BANCO_URL:...}`); nada de perfis por ambiente no Marco 0.
- `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, Hikari `connection-timeout` de 5 s (falha rápida; a verificação de saúde responde em poucos segundos com o banco fora).
- Testes: `*Test` = unitários/slice, sem Docker (Surefire, `./mvnw test`); `*IT` = integração com contexto completo + Testcontainers (Failsafe, `./mvnw verify`), sempre anotados com `@TesteDeIntegracao`. Métodos de teste em português, no formato `deve<Resultado>Quando<Condicao>()`. Ver ADR-011.

## Frontend — estrutura de pastas

Projeto em `frontend/` (pacote npm `kontra-frontend`).

```
frontend/
├── index.html, package.json, package-lock.json
├── vite.config.ts          # plugin React, alias @ → src, proxy /api, config do Vitest
├── tsconfig*.json          # strict
├── .oxlintrc.json          # lint (ADR-009); ignora src/api/generated
├── orval.config.ts         # entrada: ../backend/openapi/openapi.json
├── Dockerfile, nginx.conf, .dockerignore   # mantidos pelo devops
└── src/
    ├── main.tsx            # ponto de entrada (convenção do Vite): <ProvedoresDaAplicacao> + <RouterProvider>
    ├── app/
    │   ├── provedores.tsx  # ProvedoresDaAplicacao: ThemeProvider + CssBaseline (MUI) + QueryClientProvider
    │   ├── roteador.tsx    # createBrowserRouter: "/" → PaginaInicial, "*" → PaginaNaoEncontrada
    │   └── tema.ts         # tema MUI com locale ptBR
    ├── api/generated/      # GERADO pelo orval — nunca editar à mão; versionado
    │   ├── endpoints/<tag>/  # hooks TanStack Query + funções fetch por tag (ex.: endpoints/saude/)
    │   └── model/            # tipos dos schemas (SaudeResponse, ProblemaResponse, ...)
    ├── lib/
    │   ├── cliente-api.ts  # mutator do orval: requisicaoApi + classe ErroApi
    │   └── cliente-query.ts # criarQueryClient (defaults)
    ├── components/         # componentes compartilhados (ex.: LayoutPrincipal.tsx)
    ├── features/<modulo>/  # componentes, hooks e schemas Zod de uma funcionalidade
    │   └── saude/StatusDoSistema.tsx (+ StatusDoSistema.test.tsx)
    ├── routes/             # páginas ligadas às rotas (PaginaInicial, PaginaNaoEncontrada)
    └── test/
        ├── configuracao.ts # setup do Vitest: @testing-library/jest-dom
        └── renderizar.tsx  # renderizarComProvedores (QueryClient com retry: false + tema + MemoryRouter)
```

Regras:
- Toda chamada à API passa por hooks/funções gerados em `src/api/generated` + TanStack Query; query keys vêm das funções geradas (`getObterSaudeQueryKey()`), nunca escritas à mão.
- `requisicaoApi` (mutator): usa a URL relativa do contrato (`/api/...`), envia/recebe JSON e devolve o **corpo** tipado (ADR-003). Para status não-2xx lança `ErroApi { status, problema?: ProblemaResponse, corpo?: unknown }` — `problema` preenchido quando o `Content-Type` é `application/problem+json`; `corpo` continua `unknown` (pode ser, por exemplo, o HTML de um 502 do nginx). Erros de rede propagam como estão (`TypeError` do `fetch`). Uma resposta 2xx com `Content-Type` JSON e corpo inválido lança `SyntaxError` (do parse), que pela regra de retry **não** é repetido — esse erro chega ao componente, que o trata no estado de erro como qualquer outro.
- `cliente-api.ts` também exporta `export type ErrorType<_ErrosDoContrato> = ErroApi | Error`, que o orval usa como `TError` dos hooks gerados: o tipo reflete o erro real em tempo de execução (`ErroApi` para não-2xx, `Error`/`TypeError` para falha de rede). O nome `ErrorType` é imposto pelo orval (exceção do ADR-013).
- Defaults do `QueryClient` (`criarQueryClient`): `staleTime` 30 s; `retry` até 1 vez **somente** para `ErroApi` com status 5xx e para `TypeError` (falha de rede); qualquer outro erro (4xx, erro de programação etc.) não é repetido; mutations sem retry.
- Nomes de arquivo: componentes em PascalCase (`StatusDoSistema.tsx`); demais módulos em kebab-case (`cliente-api.ts`), sempre em português.
- Testes colocados ao lado do código (`*.test.tsx`, descrições em português), com `renderizarComProvedores`; rede simulada com `vi.spyOn(globalThis, 'fetch')` e corpos tipados com os tipos gerados (`satisfies SaudeResponse`). MSW fica para marco futuro (ADR-001).
- React Hook Form + Zod entram no primeiro marco com formulário (não são instalados no Marco 0).

### E2E

Projeto Playwright independente em `e2e/` (pacote npm `kontra-e2e`, `@playwright/test`), mantido pelo `qa`. Testes em `e2e/tests/<assunto>.spec.ts`, com títulos em português. Roda contra o ambiente do docker compose (`URL_BASE_E2E`, padrão `http://localhost:3000`); não sobe servidor próprio (`webServer` não é usado). Projeto único: Chromium.

## Modelo de dados

**Marco 0: nenhuma entidade de domínio.** A única tabela é `flyway_schema_history`, gerenciada pelo Flyway. A migração `V1__base_inicial.sql` não cria objetos; existe para validar o pipeline de migrações.

Convenções que valem a partir do Marco 1:
- Tabelas e colunas em português, `snake_case`, sem acento; tabelas no singular (`contrato`), colunas como `data_inicio`; FKs `<entidade>_id` (`contrato_id`).
- Colunas de auditoria `criado_em` / `atualizado_em` do tipo `timestamptz`.
- Estratégia de chave primária (UUID vs. `bigint` identity): decidir no Marco 1, em ADR.
- Toda mudança de esquema é uma migração nova (`V<n>__<descricao_em_portugues>.sql`); migração aplicada nunca é editada.

## Formato padronizado de erro

Todas as respostas de erro usam **RFC 9457 (Problem Details)**, `Content-Type: application/problem+json`. Os campos padrão do RFC (`type`, `title`, `status`, `detail`, `instance`) mantêm o nome; as três extensões do projeto são em português (`codigo`, `ocorridoEm`, `erros`). Implementação: `ProblemDetail` do Spring, montado por uma única `FabricaDeProblemas`, usada tanto pelo `@RestControllerAdvice` quanto pelos tratadores de 401/403 do Spring Security (que rodam fora do MVC).

```json
{
  "type": "about:blank",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "A requisição contém campos inválidos.",
  "instance": "/api/exemplo",
  "codigo": "ERRO_VALIDACAO",
  "ocorridoEm": "2026-10-06T21:00:00.123Z",
  "erros": [
    { "campo": "nome", "mensagem": "não deve estar em branco" }
  ]
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `type` | string (`format: uri-reference`) | sim | Sempre `about:blank` (fixado pela `FabricaDeProblemas`; no Spring 7 o `ProblemDetail` não tem mais `type` padrão). A semântica fina vem de `codigo`. |
| `title` | string | sim | Título curto em pt-BR, fixo por `codigo` (tabela abaixo). O RFC permite título localizado com `about:blank`. |
| `status` | integer | sim | Código HTTP **real** da resposta (pode diferir do status usual do `codigo` — ver regra de mapeamento). |
| `detail` | string | sim | Mensagem em pt-BR, segura para exibir ao usuário; sempre presente (específica do caso ou o detalhe padrão do código). Nunca contém stack trace, SQL ou nome de classe. |
| `instance` | string (`format: uri-reference`) | não | Caminho da requisição (omitido se o caminho não formar uma URI válida). `type` e `instance` são "URI reference" na RFC 9457, por isso o schema usa `uri-reference` (e não `uri`, que exigiria URI absoluta); ajustado na T6. |
| `codigo` | `CodigoErro` (enum) | sim | Código estável, para o frontend decidir o tratamento. |
| `ocorridoEm` | string (date-time, UTC) | sim | Momento do erro, com precisão de milissegundos. |
| `erros` | `ErroDeCampo[]` | não | Só em `ERRO_VALIDACAO`: `{ campo: string, mensagem: string }` (ambos obrigatórios), **ordenados por `campo` e depois por `mensagem`** (saída determinística). `campo` = caminho do campo inválido (ex.: `nome`, `itens[0].quantidade`); `requisicao` para restrições de classe (validação do objeto inteiro — nunca o nome da classe Java); `parametros` para restrições entre parâmetros de método. |

Schemas no OpenAPI: `ProblemaResponse`, `ErroDeCampo` e o enum `CodigoErro` (records/enum em `comum/erro`, usados nas anotações `@ApiResponse`), com `requiredMode = REQUIRED` nos campos marcados como obrigatórios acima e `enumAsRef = true` no `CodigoErro`. Um teste de integração garante que o JSON real de erro bate com esses campos. O `Content-Type` é sempre `application/problem+json`, mesmo que o cliente envie `Accept: application/json`.

`CodigoErro` (Marco 0):

| `codigo` | HTTP usual | `title` | Quando |
|---|---|---|---|
| `ERRO_VALIDACAO` | 400 | Dados inválidos | Bean Validation em corpo/parâmetros (`MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException`). |
| `REQUISICAO_MALFORMADA` | 400 | Requisição malformada | Qualquer outro 400 do framework: JSON ilegível, tipo de parâmetro inválido, parâmetro/parte/cabeçalho obrigatório ausente. |
| `NAO_AUTENTICADO` | 401 | Não autenticado | Rota não pública sem autenticação (no Marco 0: toda rota não pública, inclusive inexistente). |
| `ACESSO_NEGADO` | 403 | Acesso negado | Autenticado sem permissão (sem uso real no Marco 0). |
| `NAO_ENCONTRADO` | 404 | Recurso não encontrado | Recurso/rota inexistente em caminho público. |
| `METODO_NAO_PERMITIDO` | 405 | Método não permitido | Método HTTP não suportado. |
| `CONFLITO` | 409 | Conflito | Reservado para regras de negócio/unicidade (a partir do Marco 1). |
| `TIPO_DE_MIDIA_NAO_SUPORTADO` | 415 | Tipo de mídia não suportado | `Content-Type` enviado não suportado. |
| `ERRO_REQUISICAO` | 400 (usual); na prática o 4xx real | Requisição não atendida | **Qualquer 4xx sem código próprio**, com o status HTTP real preservado — ex.: 406 (formato de resposta não aceitável), 413 (conteúdo grande demais), 414, 431. |
| `ERRO_INTERNO` | 500 (ou o 5xx real) | Erro interno | Qualquer exceção não tratada e qualquer 5xx do framework. `detail` genérico; stack trace só no log. |

**Regra de mapeamento status ↔ código** (`CodigoErro.deStatus`, para exceções do framework sem tratamento específico): 400 → `REQUISICAO_MALFORMADA`; 401, 403, 404, 405, 409, 415 → o código da tabela; **qualquer outro 4xx → `ERRO_REQUISICAO`**; qualquer 5xx → `ERRO_INTERNO`. O `status` da resposta é sempre o real. Códigos específicos novos (ex.: `ARQUIVO_MUITO_GRANDE` para 413) só entram quando uma funcionalidade tornar o caso esperado para o usuário (ex.: upload de arquivos) — é uma mudança de contrato, registrada no plano do marco. O frontend trata `codigo` com um caso padrão (exibir `detail`) para os códigos genéricos.

`TratadorGlobalDeExcecoes` (`@RestControllerAdvice`, estende `ResponseEntityExceptionHandler`): sobrescreve a criação da resposta para todas as exceções do Spring MVC passarem pela `FabricaDeProblemas` (mantendo status e cabeçalhos, ex.: `Allow`); trata `ExcecaoApi`, as exceções de validação e `Exception` (500); repassa `AccessDeniedException`/`AuthenticationException` ao Spring Security. Os tratadores devolvem `ResponseEntity` (sem `@ResponseStatus`), para o springdoc não injetar respostas genéricas em todas as operações — as respostas de erro de cada operação são declaradas explicitamente.

**Erros fora do Spring MVC** também seguem o contrato:
- **`/error`**: o `ErroPadraoController` (`comum/erro`, implementa `ErrorController`) substitui o do Boot e responde pela `FabricaDeProblemas`: status lido de `ERROR_STATUS_CODE` (ausente → 404), `codigo` por `CodigoErro.deStatus`, `instance` = URI original da requisição (`ERROR_REQUEST_URI`). Caminho em `spring.web.error.path` (padrão `/error`); página Whitelabel desligada com `spring.web.error.whitelabel.enabled: false`. **No Spring Boot 4 a família `server.error.*` está depreciada e não é lida — usar sempre `spring.web.error.*`.** O `/error` fica fora do OpenAPI (ver T5 do Marco 0).
- **Firewall do Spring Security**: o `TratadorRequisicaoRejeitada` (`comum/erro`, implementa o `RequestRejectedHandler` do Security 7) escreve as rejeições do `StrictHttpFirewall` direto pela `FabricaDeProblemas`: 400 `REQUISICAO_MALFORMADA`, com `detail` específico. Casos do firewall: `;` no caminho, `//`, `%2e%2e` (travessia codificada), dupla codificação (`%252e`) e método HTTP desconhecido.
- **Cabeçalhos de segurança**: as respostas do firewall e do `/error` levam `X-Content-Type-Options: nosniff` e `Cache-Control: no-store`, escritos pela própria `FabricaDeProblemas`/`ErroPadraoController` — o `HeaderWriterFilter` do Spring Security não atua nesses caminhos.
- **Métodos HTTP incomuns**: `TRACE` é barrado pelo próprio Tomcat e chega pelo `/error` como 405 `METODO_NAO_PERMITIDO`; métodos desconhecidos (ex.: `FOO`) são rejeitados pelo firewall → 400 `REQUISICAO_MALFORMADA`.
- **Única lacuna aceita**: o que o próprio Tomcat recusa **antes** do Spring Security e do servlet sai como a página HTML 400 do Tomcat, sem detalhe interno (não depende da aplicação). Casos medidos: barra codificada (`/api/a%2Fb`), `%` solto, URI acima de 8 KB e cabeçalho acima de 8 KB. Aceito no Marco 0.

## Decisões técnicas (ADRs)

### ADR-001 — Versões da stack (verificadas em 2026-10-06)

**Contexto.** O `CLAUDE.md` pede a versão estável mais recente; as versões foram consultadas em start.spring.io, Maven Central, registro npm e Docker Hub nesta data.

**Decisão.**

| Item | Versão | Observação |
|---|---|---|
| Java | 21 (Temurin) | LTS instalada no ambiente; Boot 4.1 suporta 17–27. Migrar para 25 LTS é decisão futura. |
| Spring Boot | **4.1.1** | Padrão atual do Initializr (4.2.0 está em M2). Starters do Boot 4: `spring-boot-starter-webmvc`, `-data-jpa`, `-validation`, `-security`, `-flyway` (+ `flyway-database-postgresql`) e os `*-test` correspondentes. |
| Gerenciadas pelo Boot 4.1.1 | Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate 7.4.5, Jackson 3.1.5, Flyway 12.4.0, PostgreSQL JDBC 42.7.13, Testcontainers 2.0.5, JUnit Jupiter 6.0.3, Mockito 5.23.0 | Não sobrescrever versões gerenciadas (Flyway 13.x existe, mas fica a do Boot). |
| springdoc-openapi | **3.1.1** (`springdoc-openapi-starter-webmvc-ui`) | Linha 3.x é a do Boot 4; a 3.1.1 é compilada contra Spring Boot 4.1.0 (parent do pom) — compatível com 4.1.1. |
| Testcontainers | 2.0.5 (via Boot) | Artefatos renomeados no 2.x: `testcontainers-postgresql`, `testcontainers-junit-jupiter`. Compatível com Docker Engine 29. |
| PostgreSQL | **18** (`postgres:18-alpine`) | Mesma imagem no compose e no Testcontainers. |
| Node.js | 24 (LTS) | Local 24.19; CI e Docker `node:24-alpine`. |
| React / React DOM | 19.3.0 | |
| Vite / @vitejs/plugin-react | 8.3.3 / 6.1.2 | |
| TypeScript | **~6.0.3** (não 7.x) | TS 7.0 é o `latest` no npm, mas `typescript-eslint` exige `<6.1` e `openapi-typescript` exige `^5`; o template oficial do create-vite 9.2 fixa `~6.0.2`. Reavaliar TS 7 quando o ecossistema acompanhar. |
| TanStack Query | 5.104.1 | |
| React Router | 8.4.0 (pacote `react-router`, modo data: `createBrowserRouter`) | Major recente (jun/2026). |
| MUI | 9.4.0 (`@mui/material` + `@emotion/react` 11.14 + `@emotion/styled` 11.14) | ADR-002. |
| orval | **8.40.0** (fixa, sem `^`) | ADR-003. Versão fixa para a saída gerada ser determinística. |
| Lint | oxlint 1.87 | ADR-009. |
| Testes frontend | Vitest 5.0.3, @testing-library/react 16.3, @testing-library/dom 10.4, @testing-library/jest-dom 7.0, @testing-library/user-event 14.6, jsdom 30 | |
| Playwright | @playwright/test 1.63.0 | |
| Formulários (a partir do 1º marco com formulário) | react-hook-form 7.89, zod 4.6, @hookform/resolvers 5.9 | Não instalados no Marco 0. |
| Scripts de instalação npm | `"allowScripts": { "esbuild": false }` no `frontend/package.json` | Política explícita: o `postinstall` do esbuild é desnecessário — o binário vem pelo pacote opcional `@esbuild/<plataforma>` (comprovado com `--ignore-scripts`; o npm 12 já bloqueia scripts por padrão). Novos pacotes com script de instalação entram na lista só com justificativa. |
| Imagens Docker | `eclipse-temurin:21-jdk` (build), `eclipse-temurin:21-jre-alpine` (runtime), `node:24-alpine` (build), `nginxinc/nginx-unprivileged:1.30-alpine` (runtime) | |
| GitHub Actions | `actions/checkout@v7`, `actions/setup-java@v6`, `actions/setup-node@v7`, `actions/upload-artifact@v7` | |

**Consequências.** Majors recentes (Boot 4, Vite 8, React Router 8, MUI 9, Vitest 5) podem ter APIs diferentes da memória dos agentes: consultar a documentação oficial da versão. Se uma versão bloquear, o agente avisa e propõe fixar a major anterior; o arquiteto atualiza este ADR. O `CLAUDE.md` cita "JUnit 5": no Boot 4.1 a JUnit Jupiter é a 6.x, com a mesma API Jupiter.

### ADR-002 — Biblioteca de UI: MUI (Material UI 9)

**Status.** Aceito (confirmado pelo usuário em 2026-10-06).

**Contexto.** Domínio ainda não definido; o frontend é implementado por agente e precisa de telas acessíveis, responsivas e consistentes, com formulários e, provavelmente, tabelas e seletores de data (aplicação de gestão).

**Decisão.** MUI (`@mui/material`), com tema próprio em `src/app/tema.ts` e locale `ptBR`. Sem misturar outra biblioteca de componentes nem Tailwind. Fonte: pilha de fontes do sistema no tema (sem CDN). Alternativa descartada: shadcn/ui + Tailwind — mais leve e com código próprio, mas exige montar e manter cada componente no repositório (mais superfície de revisão) e não traz componentes complexos prontos (tabela de dados, data/hora com locale).

**Consequências.** Componentes acessíveis prontos e pouco CSS próprio; bundle maior e runtime do Emotion (mitigar com imports nomeados e rotas com `lazy` quando crescer). Componentes avançados (DataGrid, Date Pickers do MUI X) entram quando o domínio pedir, em ADR próprio.

### ADR-003 — Cliente da API: orval (TanStack Query + fetch + mutator)

**Contexto.** O cliente TypeScript tem de ser gerado do OpenAPI. Candidatos: orval e openapi-typescript.

**Decisão.** orval 8.40.0 com `client: 'react-query'`, `httpClient: 'fetch'`, `mode: 'tags-split'`, `clean: true`, saída em `src/api/generated/` (`endpoints/` e `model/`) e `override.mutator` apontando para `src/lib/cliente-api.ts` (`requisicaoApi`). **`override.fetch.includeHttpResponseReturnType: false`**: funções e hooks devolvem o **corpo** tipado (ex.: `obterSaude(): Promise<SaudeResponse>`), sem o envelope `{ data, status, headers }`; status de erro chega pelo `ErroApi`. Tipo de erro dos hooks: `ErrorType` exportado pelo mutator (`ErroApi | Error`). Sem geração de mocks no Marco 0.

**Consequências.** Hooks TanStack Query e query keys gerados — nada de tipos ou chaves escritos à mão. Os nomes gerados seguem o `operationId` em português com os prefixos do orval (`useObterSaude`, `getObterSaudeQueryKey`). openapi-typescript foi descartado também por exigir `typescript ^5` como peer (conflita com o TS 6.0 do projeto). A forma de tratar erros fica concentrada no mutator. Sem o envelope, status e cabeçalhos de **sucesso** não ficam acessíveis: se um endpoint futuro precisar deles (ex.: `Location` em um 201, `ETag`), a forma de expô-los é decidida em ADR nessa hora.

### ADR-004 — Snapshot do OpenAPI versionado e cliente gerado versionado

**Contexto.** O cliente precisa ser gerado sem o backend rodando (inclusive no CI), e divergências entre código, contrato e cliente devem quebrar o build.

**Decisão.**
1. **Quem gera o spec**: o backend, pelo teste `SnapshotOpenApiIT` (contexto completo + Testcontainers + MockMvc em `GET /v3/api-docs`).
2. **Onde fica**: `backend/openapi/openapi.json`, versionado, JSON indentado com chaves ordenadas (`springdoc.writer-with-order-by-keys=true`, `springdoc.writer-with-default-pretty-printer=true`) e `servers` fixo (`/`) + `info` fixo no `OpenApiConfig` — saída determinística. Só entram rotas sob `/api/**` (`springdoc.paths-to-match`), então `/error` e qualquer rota técnica ficam fora do contrato.
3. **Divergência no backend**: o teste compara o spec gerado com o arquivo como árvore JSON (imune a CRLF/LF). Diferente → falha com a mensagem "rode `./mvnw verify -Dopenapi.atualizarSnapshot=true`". Com a propriedade `openapi.atualizarSnapshot=true`, o teste regrava o arquivo e passa. Toda tarefa de backend que muda o contrato commita o snapshot atualizado junto.
4. **Quem gera o cliente**: o frontend, com `npm run generate:api` (lê `../backend/openapi/openapi.json`, só leitura). `src/api/generated/` é versionado e excluído do lint.
5. **Divergência no frontend**: o CI roda `npm run generate:api` e `git diff --exit-code -- src/api/generated`; diferença → falha.
6. **Proteções adicionais**: `springdoc.override-with-generic-response: false` (as respostas de erro de cada operação são só as declaradas; nada é injetado a partir do `@RestControllerAdvice`); o `SnapshotOpenApiIT` recusa regravar o arquivo quando a variável de ambiente `CI` está definida; o CI roda `git diff --exit-code -- backend/openapi/openapi.json` depois do `./mvnw -B verify` (pega `-D` indevido no workflow ou snapshot não commitado); e um teste confere que as chaves do JSON real dos DTOs batem com `properties`/`required` do schema publicado — o springdoc usa Jackson 2 e a aplicação Jackson 3 (ver "Backend — pacotes", regra "DTOs do contrato e Jackson").

**Consequências.** Mudança de contrato = commit de backend (código + snapshot) seguido de commit de frontend (cliente regenerado). O build Docker do frontend não precisa do backend nem do spec. O frontend nunca edita o snapshot nem o código gerado. Divergência entre o JSON real e o schema publicado quebra o build do backend.

### ADR-005 — Prefixo `/api` e mesma origem (sem CORS)

**Contexto.** Frontend e backend precisam conversar em dev e no compose sem configuração por ambiente no cliente.

**Decisão.** Todos os controllers REST sob `/api/**` (mapeamento explícito em cada controller). O cliente gerado usa URLs relativas. Em dev, o Vite repassa `/api` para `DESTINO_PROXY_API` (padrão `http://localhost:8080`); no compose, o nginx do frontend faz `proxy_pass http://backend:8080` para `/api/` e serve a SPA com fallback para `index.html`. CORS não é habilitado no backend.

**Consequências.** Nenhuma variável `VITE_*` de URL de API; cookies futuros funcionam como first-party. Swagger UI e `/v3/api-docs` ficam fora de `/api` e são acessados direto no backend (porta 8080).

### ADR-006 — Erros no formato RFC 9457 (Problem Details)

**Contexto.** O `CLAUDE.md` exige formato de erro padronizado e `@ControllerAdvice` global.

**Decisão.** `ProblemDetail` com extensões `codigo`, `ocorridoEm`, `erros`, conforme a seção "Formato padronizado de erro". Uma única `FabricaDeProblemas` para MVC e Spring Security. Saída determinística: `type` fixo `about:blank`, `ocorridoEm` truncado em milissegundos, `erros[]` ordenado por `campo` (e depois `mensagem`), `detail` sempre presente. Mapeamento de status sem tratamento específico: 400 → `REQUISICAO_MALFORMADA`, demais 4xx sem código próprio → `ERRO_REQUISICAO` (status real preservado), 5xx → `ERRO_INTERNO` *(decidido em 2026-10-06, a partir da T4)*.

**Consequências.** Padrão de mercado, suportado nativamente pelo Spring; o frontend tem `ProblemaResponse` e `CodigoErro` gerados e mapeia `erros[]` para os campos do formulário. Com o código genérico `ERRO_REQUISICAO`, nenhum 4xx incomum é rotulado com um significado falso (ex.: 413 como "malformada"), e o enum só cresce quando uma funcionalidade torna um caso esperado.

### ADR-007 — Spring Security no Marco 0: negar por padrão, sem autenticação

**Contexto.** Spring Security está na stack, mas o mecanismo de autenticação (sessão, JWT, OAuth2/OIDC...) será decidido em marco futuro.

**Decisão.** Um `SecurityFilterChain` (em `SegurancaConfig`) com:
- públicas (`permitAll`): `GET /api/saude`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/error`;
- demais rotas: `authenticated()` — como não há mecanismo de login, resultam em **401** `NAO_AUTENTICADO` (Problem Details) via `TratadorNaoAutenticado` (`AuthenticationEntryPoint`); 403 via `TratadorAcessoNegado` (`AccessDeniedHandler`);
- `httpBasic`, `formLogin` e `logout` desabilitados; usuário em memória gerado pelo Boot desligado (o log não pode conter `Using generated security password`) por **propriedade** — `spring.autoconfigure.exclude: org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration` no `application.yml` —, e não por anotação, porque os testes de fatia (`@WebMvcTest`) só respeitam a propriedade;
- sessão `STATELESS`; CSRF desabilitado (não há autenticação por cookie nem endpoint de escrita) — **o marco de autenticação deve reavaliar CSRF obrigatoriamente**;
- sem CORS (ADR-005); cabeçalhos de segurança padrão do Spring Security mantidos.

**Consequências.** Rotas novas nascem protegidas; qualquer rota pública nova exige alteração explícita da `SegurancaConfig` (revisada). Swagger público em todos os ambientes no Marco 0 (não existe produção); pode ser desligado por variável (`SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`). Com o Swagger UI público, `springdoc.swagger-ui.query-config-enabled` **deve permanecer `false`** (padrão): impede carregar spec/configuração externa via `?url=`/`?configUrl=`, vetor clássico de XSS do Swagger UI. `/v3/api-docs.yaml` responde 401 (fica fora do `permitAll` de `/v3/api-docs/**`) — aceitável; o contrato publicado é o JSON.

**Pendências obrigatórias do marco de autenticação** (e itens da auditoria de segurança):
- reavaliar CSRF conforme o mecanismo escolhido (obrigatório reabilitar se houver autenticação por cookie);
- o 401 do Marco 0 sai **sem** o cabeçalho `WWW-Authenticate`, que a RFC 9110 exige em toda resposta 401 — não há esquema de autenticação a anunciar. Ao escolher o mecanismo, o `TratadorNaoAutenticado` passa a enviar o desafio correspondente (ex.: `Bearer`);
- `HEAD /api/saude` responde 401, porque só o `GET` é público (os healthchecks usam `GET` — sem impacto no Marco 0). Decidir se `HEAD` acompanha o `GET` nas rotas públicas.

### ADR-008 — Verificação de saúde própria em `/api/saude` em vez do Actuator

**Contexto.** O frontend precisa consumir a verificação de saúde pelo cliente gerado; endpoints do Actuator não entram no springdoc por padrão e, mesmo com `springdoc.show-actuator`, não têm schema de resposta tipado.

**Decisão.** Endpoint próprio `GET /api/saude` (`SaudeController` → `SaudeService.verificarBancoDeDados()`), que verifica o banco (`DataSource.getConnection()` + `isValid(2)`), responde 200 com tudo `DISPONIVEL` ou 503 com `INDISPONIVEL`, e serve também de healthcheck do container. Actuator não é adicionado no Marco 0.

**Consequências.** Contrato tipado (`SaudeResponse`, `StatusComponente`) no cliente gerado. Métricas/observabilidade (Actuator, Prometheus) ficam para marco futuro, em ADR próprio.

### ADR-009 — Lint do frontend com oxlint

**Contexto.** O template oficial do create-vite 9.2 já vem com oxlint; `typescript-eslint` limita o TypeScript a `<6.1`.

**Decisão.** oxlint (`npm run lint`), com `.oxlintrc.json` ativando os plugins `typescript`, `react`, `react-hooks`, `jsx-a11y` e, como erro, no mínimo: `no-explicit-any`, `ban-ts-comment`, `rules-of-hooks`, `exhaustive-deps`, `jsx-key`. `src/api/generated/` ignorado. Checagem de tipos pelo `tsc -b` no `npm run build`. Sem formatador obrigatório no Marco 0.

**Consequências.** Lint muito rápido e uma dependência a menos que o ESLint; regras type-aware avançadas do typescript-eslint não estão disponíveis (o `strict` do TS cobre o essencial).

### ADR-010 — Git: commit raiz e uma branch por marco nesta fase

**Status.** Aceito (confirmado pelo usuário em 2026-10-06).

**Contexto.** A pasta ainda não é repositório git. O repositório remoto existe no GitHub (`https://github.com/guiCoutinho/Kontra`), ainda sem nenhum push. Os agentes de backend, frontend e devops trabalham **na mesma árvore de trabalho**, em paralelo; trocar de branch para cada tarefa paralela é inviável.

**Decisão.**
- `git init -b main` e **um único commit raiz na `main`** (exceção única à regra), contendo só `.gitignore`, `.gitattributes`, `CLAUDE.md`, `.claude/agents/` e `docs/`.
- Remoto `origin` = `https://github.com/guiCoutinho/Kontra`, configurado na T1. Nenhum push até o usuário pedir.
- Todo o Marco 0 numa branch `feat/marco-0-fundacao`, um commit por tarefa (Conventional Commits com escopo: `chore(repo)`, `chore(infra)`, `feat(backend)`, `feat(frontend)`, `test(e2e)`, `ci`).
- Ao fim do marco, após aprovação do usuário: `git merge --no-ff feat/marco-0-fundacao` na `main`.
- `.gitattributes`: `* text=auto eol=lf`; `*.cmd`/`*.bat` com `eol=crlf`; `backend/mvnw` com bit executável (`git update-index --chmod=+x`).
- `.claude/settings.local.json` fora do git (configuração pessoal).

**Consequências.** Histórico linear por marco. Quando o usuário liberar o push, passa a valer uma branch por funcionalidade com PR.

### ADR-011 — Organização dos testes do backend

**Contexto.** Testes com Testcontainers exigem Docker e são lentos; os unitários não.

**Decisão.** `*Test` (Surefire, `./mvnw test`): unitários com Mockito e slices (`@WebMvcTest` importando a `SegurancaConfig`), sem Docker. `*IT` (Failsafe, declarado no `pom.xml`, `./mvnw verify`): `@SpringBootTest` + MockMvc + `TestcontainersConfig` (PostgreSQL 18 com `@ServiceConnection`), compartilhado via cache de contexto — um container por execução. Os sufixos `Test`/`IT` são os padrões de inclusão do Surefire/Failsafe e ficam como estão (ADR-013).
- **Todo `*IT` usa a meta-anotação `suporte/TesteDeIntegracao`** (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfig.class)`) e nada além dela que altere o contexto (`@MockitoBean`, `@TestPropertySource`, perfis...). Assim o cache de contexto reaproveita a mesma aplicação e o mesmo container em toda a execução. Um `*IT` que precise de contexto diferente é exceção justificada no próprio teste.
- **Mockito como agente Java**: Surefire e Failsafe usam `argLine` `@{argLine} -javaagent:${org.mockito:mockito-core:jar} -Xshare:off`, com a propriedade `argLine` declarada vazia em `<properties>` do `pom.xml` (caminho do jar exposto pelo goal `properties` do `maven-dependency-plugin`). Motivo: o JDK 21+ desaconselha o auto-anexo dinâmico do inline mock maker; `-Xshare:off` evita o aviso de CDS. O `@{argLine}` (substituição tardia) já deixa o caminho pronto para JaCoCo ou outra ferramenta que preencha `argLine`: ela acrescenta seu agente sem sobrescrever o do Mockito — basta não redefinir o `<argLine>` dos plugins.

**Consequências.** `./mvnw verify` é o critério de entrega do backend e exige Docker em execução; `./mvnw test` dá retorno rápido sem Docker. Um único contexto Spring nos `*IT` mantém a suíte rápida; o revisor rejeita `*IT` sem `@TesteDeIntegracao`.

### ADR-012 — Configuração por variáveis de ambiente

**Contexto.** Configuração deve vir do ambiente, com `.env.example` na raiz.

**Decisão.** O `docker compose` lê `.env` da raiz (fora do git) e tem padrões de desenvolvimento embutidos (`${VAR:-padrão}`), então `docker compose up` funciona sem `.env`. O backend lê `BANCO_URL`, `BANCO_USUARIO`, `BANCO_SENHA` com padrões que apontam para o PostgreSQL do compose (`jdbc:postgresql://localhost:5432/kontra`, `kontra`/`kontra`) — **credenciais apenas de desenvolvimento**; qualquer ambiente real deve sobrescrevê-las. Variáveis só de dev local do frontend (`DESTINO_PROXY_API`) são lidas pelo `vite.config.ts`, não expostas ao bundle (sem prefixo `VITE_`). Variáveis impostas por terceiros mantêm o nome original (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` da imagem oficial; `SPRINGDOC_*` do binding do Spring). Lista completa no `docs/PLANO.md` (Marco 0) e no `.env.example`.
- Só o `docker compose` lê o `.env` da raiz. `BANCO_*` (backend fora do container), `DESTINO_PROXY_API` e `URL_BASE_E2E` são exportadas no shell ou na IDE quando for preciso mudar o padrão; o `.env.example` as lista como referência.
- No compose, o serviço `backend` recebe `BANCO_*` montadas a partir de `POSTGRES_*` com host `banco`; nunca interpola `BANCO_*` do `.env` (que, copiado do exemplo, apontaria para `localhost`).

**Consequências.** Um comando sobe tudo; o risco de credencial padrão em ambiente real fica registrado para a auditoria de segurança.

### ADR-013 — Convenção de idioma: tudo em português

**Status.** Aceito (decisão do usuário em 2026-10-06). Substitui a premissa anterior "código em inglês, interface em pt-BR".

**Contexto.** O usuário quer o projeto inteiro em português, inclusive código e contrato. É preciso uma regra sem ambiguidade para os agentes, com as exceções técnicas inevitáveis.

**Decisão.**

1. **Em português** (identificadores **sem acento** nem `ç`: `saude`, `configuracao`, `excecao`): rotas, campos JSON, tabelas, colunas, migrações, classes, métodos, variáveis, constantes, enums (nomes e valores), `operationId`s e tags do OpenAPI, pacotes de módulo, componentes, hooks e arquivos próprios, nomes de testes (classes, métodos, descrições), comentários, textos de interface, mensagens de erro (`title` e `detail`), variáveis de ambiente próprias, serviços do compose, propriedades de sistema próprias, commits e documentação. Textos para humanos (UI, mensagens, comentários, docs) usam acentuação normal.
2. **Ficam como são**:
   - palavras-chave da linguagem e APIs/anotações de bibliotecas e frameworks (`useState`, `@Transactional`, `createBrowserRouter`, `QueryClient`);
   - arquivos e nomes impostos pelas ferramentas: `application.yml`, `pom.xml`, `package.json`, `Dockerfile`, `docker-compose.yml`, `nginx.conf`, `vite.config.ts`, `orval.config.ts`, `playwright.config.ts`, `.oxlintrc.json`, `index.html`, `main.tsx`, prefixo `V<n>__` do Flyway, sufixos de teste `*Test`/`*IT` (padrões do Surefire/Failsafe), `*.test.tsx` (Vitest) e `*.spec.ts` (Playwright);
   - campos padrão do RFC 9457: `type`, `title`, `status`, `detail`, `instance`;
   - **vocabulário de camada do Spring**: sufixos `Controller`, `Service`, `Repository`, `Dto` (e as variantes de DTO `Request`/`Response`), e os pacotes de camada correspondentes;
   - nomes gerados por ferramentas a partir dos nossos nomes (orval: `use` + operationId, `get…QueryKey`) e nomes que a ferramenta exige de nós (orval: o tipo `ErrorType` exportado pelo mutator);
   - nomes próprios, siglas e jargão consagrado: Kontra, OpenAPI, API, JSON, URL, backend, frontend, e2e, status.
3. **Decisões do arquiteto nos pontos não cobertos pelo usuário** *(confirmado pelo usuário em 2026-10-06)*:
   - **Pacotes técnicos do backend**: `config` e `domain` ficam em inglês, como os demais pacotes de camada (`controller`, `service`, `repository`, `dto`); classes em `config/` usam o sufixo `Config` (`SegurancaConfig`, `OpenApiConfig`, `TestcontainersConfig`). Pacotes que não são camada vão para o português: `comum/erro`, `suporte` (testes), e os módulos (`saude`, `contrato`).
   - **Classe principal**: `KontraApplication` (convenção do Spring Boot gerada pelo Initializr, mesma família dos sufixos de camada).
   - **Pastas estruturais do frontend** ficam em inglês, como o `features/` do exemplo do usuário: `app/`, `api/generated/` (`endpoints/`, `model/`), `lib/`, `components/`, `features/`, `routes/`, `test/`; e `e2e/tests/`. Tudo dentro delas (módulos, componentes, arquivos) em português.
   - **Scripts npm** ficam em inglês (`dev`, `build`, `lint`, `test`, `test:watch`, `preview`, `generate:api`): são convenções do ecossistema usadas por ferramentas e pelo CI.
   - **Variáveis de ambiente próprias** em português (`BANCO_URL`, `BANCO_USUARIO`, `BANCO_SENHA`, `PORTA_POSTGRES`, `PORTA_BACKEND`, `PORTA_FRONTEND`, `DESTINO_PROXY_API`, `URL_BASE_E2E`); as impostas por terceiros ficam como são.
   - **Serviços do compose**: `banco`, `backend`, `frontend` (volume `kontra-dados-banco`).
   - **Tabelas no singular**, como no exemplo do usuário (`contrato`).

**Exemplos**

| Elemento | Regra | Exemplo |
|---|---|---|
| Rota | `/api/<recurso>`, minúsculas, coleções no plural | `/api/saude`, `/api/contratos` |
| `operationId` | verbo no infinitivo + recurso, camelCase | `obterSaude`, `listarContratos`, `criarContrato` |
| Tag OpenAPI | nome do módulo | `saude` |
| Campo JSON | camelCase | `bancoDeDados`, `verificadoEm` |
| Enum (nome / valores) | PascalCase / MAIÚSCULAS_COM_SUBLINHADO | `StatusComponente` / `DISPONIVEL`, `INDISPONIVEL` |
| Código de erro | MAIÚSCULAS_COM_SUBLINHADO | `ERRO_VALIDACAO`, `NAO_AUTENTICADO` |
| Extensão do ProblemDetail | camelCase | `codigo`, `ocorridoEm`, `erros[].campo` |
| Tabela / coluna | snake_case, tabela no singular | `contrato (id, data_inicio, contrato_id)` |
| Migração | `V<n>__<descricao>.sql` | `V1__base_inicial.sql` |
| Pacote de módulo | português | `br.com.kontra.saude`, `br.com.kontra.comum.erro` |
| Pacote de camada | vocabulário Spring | `br.com.kontra.saude.controller`, `...saude.service` |
| Classe com camada | português + sufixo | `SaudeController`, `SaudeService`, `SaudeResponse`, `ContratoRepository`, `SegurancaConfig` |
| Entidade / tipo sem camada | português | `Contrato`, `StatusComponente`, `ExcecaoApi`, `FabricaDeProblemas` |
| Método | verbo no infinitivo | `verificarBancoDeDados()`, `obterSaude()` |
| Teste backend | `<Classe>Test` / `<Assunto>IT`; método `deve…Quando…` | `SaudeServiceTest.deveRetornarIndisponivelQuandoBancoFalhar()`, `SaudeIT` |
| Componente React | PascalCase | `StatusDoSistema`, `PaginaInicial` |
| Módulo TS | kebab-case | `cliente-api.ts`, `cliente-query.ts` |
| Função / hook próprio | camelCase / `use` + nome | `criarQueryClient`, `useFiltroDeContratos` |
| Hook gerado | `use` + operationId | `useObterSaude`, `getObterSaudeQueryKey` |
| Teste frontend | `<Arquivo>.test.tsx`, descrições em português | `it('exibe "Operacional" quando a API responde')` |
| Teste e2e | `<assunto>.spec.ts`, títulos em português | `e2e/tests/saude.spec.ts` |
| Variável de ambiente | MAIÚSCULAS | `BANCO_URL`, `PORTA_FRONTEND` |
| Serviço do compose | minúsculas | `banco` |
| Branch / commit | português (tipos do Conventional Commits como são) | `feat/marco-0-fundacao`, `feat(backend): adiciona verificação de saúde` |

**Locale fixo.** O backend usa `spring.web.locale: pt_BR` com `spring.web.locale-resolver: fixed`: mensagens do Bean Validation (e demais mensagens resolvidas por locale) saem sempre em português, independentemente do `Accept-Language` do cliente e do locale da JVM/SO. Mensagens personalizadas de validação também são escritas em português. Impactos:
- `Accept-Language` é **ignorado** pela API (intencional: a aplicação só fala português).
- `@DateTimeFormat`/`@NumberFormat` sem `iso` passariam a interpretar no formato pt-BR (ex.: `06/10/2026`, vírgula decimal). **Na API, sempre `iso = ...`** (datas/horas ISO-8601, números com ponto) — o contrato usa ISO-8601.
- Validação fora de uma requisição HTTP (jobs, inicialização) usa o locale da JVM, não o `spring.web.locale`. Por isso a imagem Docker do backend fixa `JAVA_TOOL_OPTIONS=-Duser.language=pt -Duser.country=BR` (T11 do Marco 0); fora do container, vale o locale da máquina.

**Consequências.** Código e contrato legíveis para o time em português; o cliente gerado herda os nomes do `operationId`. Convivem trechos em inglês impostos por frameworks e ferramentas (`useObterSaude`, `getObterSaudeQueryKey`, `SaudeController`) — esperado e aceito. O revisor passa a fiscalizar a convenção.
