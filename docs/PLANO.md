# Plano — Kontra

> Mantido pelo agente `arquiteto`. Um bloco por marco. Arquitetura, convenções e ADRs em `docs/ARQUITETURA.md`.

## Marco 0 — Fundação

> Planejado em 2026-10-06 e aprovado pelo usuário na mesma data. Estado de partida verificado: só existem `.claude/`, `CLAUDE.md` e `docs/`; a pasta não é repositório git. Remoto no GitHub: `https://github.com/guiCoutinho/Kontra`, configurado como `origin` na T1, sem push. Todos os nomes seguem a convenção de idioma do ADR-013 (tudo em português).

### Objetivo

Deixar o monorepo pronto para desenvolver funcionalidades, provando a cadeia inteira com uma funcionalidade mínima:

- estrutura do monorepo + git inicializado com `.gitignore`/`.gitattributes`;
- backend Spring Boot 4.1.1 (Java 21) com PostgreSQL 18, Flyway, springdoc, Spring Security (negar por padrão), formato de erro RFC 9457 e testes (unitários + Testcontainers);
- snapshot versionado do OpenAPI e cliente TypeScript gerado pelo orval a partir dele, com detecção de divergência;
- frontend React 19 + Vite 8 + TS 6.0 + MUI 9 + TanStack Query + React Router, com uma tela que exibe a **verificação de saúde** vinda do backend pelo cliente gerado;
- `docker compose up -d --build --wait` sobe banco, backend e frontend (nginx com proxy de `/api`);
- um teste e2e Playwright mínimo contra o compose;
- workflow do GitHub Actions (backend, frontend, e2e) validado localmente.

### Fora de escopo

- Qualquer domínio de negócio, entidade ou tabela (além do `flyway_schema_history`).
- Autenticação, usuários, perfis, permissões, CSRF definitivo, CORS.
- Actuator, métricas, logs estruturados, tracing.
- React Hook Form, Zod, MSW, mocks gerados pelo orval, i18n além do locale do MUI.
- Deploy, perfis de produção, HTTPS, segredos reais, Dependabot, cobertura mínima, formatador de código.
- Push para o `origin` e PRs (só quando o usuário pedir).

### Contrato da API

Snapshot gerado: `backend/openapi/openapi.json` (OpenAPI 3.1; `info.title` = `API do Kontra`, `info.version` = `0.1.0`, `servers` = `[{ "url": "/" }]`).

#### `GET /api/saude`

| Item | Valor |
|---|---|
| Acesso | Público (sem autenticação) |
| `operationId` / tag | `obterSaude` / `saude` → hook gerado `useObterSaude` |
| Entrada | Nenhuma |
| Cabeçalho de resposta | `Cache-Control: no-store` |
| Comportamento | `SaudeService.verificarBancoDeDados()`: `DataSource.getConnection()` + `Connection.isValid(2)`; Hikari `connection-timeout` 5 s. `status` = `DISPONIVEL` se e somente se todos os componentes estão `DISPONIVEL`. |

Respostas:

| Status | Content-Type | Corpo | Quando |
|---|---|---|---|
| 200 | `application/json` | `SaudeResponse` (`status` e `bancoDeDados` = `DISPONIVEL`) | Banco acessível |
| 503 | `application/json` | `SaudeResponse` (`status` e `bancoDeDados` = `INDISPONIVEL`) | Banco inacessível |
| 500 | `application/problem+json` | `ProblemaResponse` (`codigo` = `ERRO_INTERNO`) | Erro inesperado |

**`SaudeResponse`**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `status` | `StatusComponente` | sim | Status agregado |
| `bancoDeDados` | `StatusComponente` | sim | Status da conexão com o PostgreSQL |
| `verificadoEm` | string (`date-time`, UTC) | sim | Momento da verificação |

**`StatusComponente`** — enum `DISPONIVEL` \| `INDISPONIVEL`, schema nomeado (`@Schema(enumAsRef = true)`).

Exemplos:

```json
// 200
{ "status": "DISPONIVEL", "bancoDeDados": "DISPONIVEL", "verificadoEm": "2026-10-06T21:00:00Z" }
// 503
{ "status": "INDISPONIVEL", "bancoDeDados": "INDISPONIVEL", "verificadoEm": "2026-10-06T21:00:05Z" }
```

#### Schemas comuns (formato de erro)

`ProblemaResponse`, `ErroDeCampo` e `CodigoErro` (enum nomeado, `enumAsRef = true`), conforme `docs/ARQUITETURA.md` → "Formato padronizado de erro" (extensões `codigo`, `ocorridoEm`, `erros[]` com `campo`/`mensagem`; `title` em pt-BR por código; `detail` sempre presente; 4xx sem código próprio → `ERRO_REQUISICAO` com o status real, 5xx → `ERRO_INTERNO`). Precisam aparecer em `components.schemas` do snapshot já no Marco 0 (referenciados pela resposta 500 de `/api/saude`), para o frontend tipar o `ErroApi`.

#### Comportamento global (não entra no cliente gerado)

| Requisição | Resultado |
|---|---|
| Qualquer rota fora da lista pública (inclusive inexistente), ex.: `GET /api/inexistente` | 401, `application/problem+json`, `codigo` = `NAO_AUTENTICADO`, sem `Set-Cookie` |
| `GET /v3/api-docs`, `GET /swagger-ui.html` (e `/swagger-ui/**`) | 200 (ou redirecionamento para `/swagger-ui/index.html`), sem autenticação |
| `POST /api/saude` (ou outro método além de `GET`, inclusive `HEAD`) | 401 `NAO_AUTENTICADO` — a regra pública vale só para `GET` (pendência registrada no ADR-007) |
| Erro despachado para `/error` (fora do Spring MVC) | Problem Details pelo `ErroPadraoController`: status original (ausente → 404), `codigo` por `CodigoErro.deStatus`, `instance` = URI original |
| `TRACE` em qualquer rota | 405 `METODO_NAO_PERMITIDO` (barrado pelo Tomcat, respondido pelo `/error`) |
| Método desconhecido (ex.: `FOO`) | 400 `REQUISICAO_MALFORMADA` (rejeitado pelo firewall, `TratadorRequisicaoRejeitada`) |
| URL rejeitada pelo firewall do Spring Security (ex.: `;`, `//`, `%2e%2e`, dupla codificação `%252e`) | 400 `REQUISICAO_MALFORMADA` (Problem Details, `TratadorRequisicaoRejeitada`, `detail` específico) |
| Requisição recusada pelo Tomcat antes do servlet (barra codificada `/api/a%2Fb`, `%` solto, URI ou cabeçalho acima de 8 KB) | 400 no formato do próprio Tomcat, sem detalhe interno — **fora do contrato** (lacuna aceita) |

### Migrações

| Arquivo | O que faz |
|---|---|
| `backend/src/main/resources/db/migration/V1__base_inicial.sql` | Não cria objetos (só comentário "base inicial do Marco 0"). Valida o pipeline do Flyway: após a subida, `flyway_schema_history` tem a versão `1` com `success = true`. Se o Flyway recusar script sem instrução, usar `SELECT 1;`. |

### Variáveis de ambiente

| Variável | Usada por | Padrão (dev) | Descrição |
|---|---|---|---|
| `POSTGRES_DB` | compose (`banco`, `backend`) | `kontra` | Nome do banco (nome imposto pela imagem oficial) |
| `POSTGRES_USER` | compose (`banco`, `backend`) | `kontra` | Usuário do banco (imposto pela imagem) |
| `POSTGRES_PASSWORD` | compose (`banco`, `backend`) | `kontra` | Senha — **só dev** (imposto pela imagem) |
| `PORTA_POSTGRES` | compose | `5432` | Porta do PostgreSQL no host |
| `PORTA_BACKEND` | compose | `8080` | Porta do backend no host |
| `PORTA_FRONTEND` | compose | `3000` | Porta do frontend (nginx) no host |
| `BANCO_URL` | backend | `jdbc:postgresql://localhost:5432/kontra` | JDBC URL (no compose: `jdbc:postgresql://banco:5432/${POSTGRES_DB}`) |
| `BANCO_USUARIO` | backend | `kontra` | Usuário JDBC (no compose: `${POSTGRES_USER}`) |
| `BANCO_SENHA` | backend | `kontra` | Senha JDBC — **só dev** (no compose: `${POSTGRES_PASSWORD}`) |
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | backend (opcional) | `true` | Desligam OpenAPI/Swagger UI (nomes impostos pelo binding do Spring) |
| `DESTINO_PROXY_API` | frontend (`vite.config.ts`, só `npm run dev`) | `http://localhost:8080` | Destino do proxy de `/api` |
| `URL_BASE_E2E` | e2e (`playwright.config.ts`) | `http://localhost:3000` | URL do frontend sob teste |

O `.env.example` na raiz lista todas, agrupadas por consumidor, sem segredos reais. O compose usa `${VAR:-padrão}` e funciona sem `.env`.

**Quem lê o `.env` da raiz**: só o `docker compose` (`POSTGRES_*` e `PORTA_*`). `BANCO_*` (backend rodando fora do container), `DESTINO_PROXY_API` (Vite) e `URL_BASE_E2E` (Playwright) **não** são lidas do `.env` — quando for preciso mudar o padrão, exportar no shell ou na configuração de execução da IDE. O `.env.example` as lista apenas como referência. Dentro do compose, o backend recebe `BANCO_*` montadas a partir de `POSTGRES_*` (ver T11).

### Tarefas

Legenda: cada tarefa = 1 commit na branch `feat/marco-0-fundacao` (ADR-010). Fluxo por tarefa: implementa → revisor → qa → devops commita. Nomes conforme ADR-013. Comandos de backend no Git Bash (`./mvnw`); no PowerShell use `.\mvnw.cmd`. Testes `*IT` exigem o Docker em execução.

---

**T1 — Inicializar o repositório git** · `devops`
- **Área**: raiz — `.gitignore`, `.gitattributes`.
- **Fazer**: `git init -b main`; commit raiz na `main` (`chore(repo): inicializa repositório com documentação e agentes`) contendo só `.gitignore`, `.gitattributes`, `CLAUDE.md`, `.claude/agents/`, `docs/`; criar e trocar para `feat/marco-0-fundacao`; `git remote add origin https://github.com/guiCoutinho/Kontra` (**sem push**). Ver ADR-010.
  - `.gitignore`: `.env` e `.env.*` (exceto `!.env.example`); `backend/target/`; `node_modules/`; `frontend/dist/`; `build/`; `coverage/`; `*.local`; `e2e/playwright-report/`, `e2e/test-results/`, `e2e/blob-report/`, `e2e/playwright/.cache/`; `*.log`; IDE (`.idea/`, `*.iml`, `.vscode/*` exceto `!.vscode/extensions.json`); SO (`.DS_Store`, `Thumbs.db`); `.claude/settings.local.json`.
  - `.gitattributes`: `* text=auto eol=lf`; `*.cmd text eol=crlf`; `*.bat text eol=crlf`; binários comuns (`*.png`, `*.jpg`, `*.ico`, `*.jar`, `*.woff2`) como `binary`.
- **Pronto**:
  - `git log --oneline main` → 1 commit; `git show --stat main` lista só os arquivos acima.
  - `git branch --show-current` → `feat/marco-0-fundacao`; `git status` limpo.
  - `git remote get-url origin` → `https://github.com/guiCoutinho/Kontra`; `git branch -r` → vazio (nenhum push/fetch).
  - `git check-ignore -v .env backend/target/x frontend/node_modules/x frontend/dist/x e2e/playwright-report/x .claude/settings.local.json` → todos ignorados; `git check-ignore .env.example` → nada (exit 1).
- **Depende de**: —.

**T2 — docker compose com PostgreSQL + `.env.example` inicial** · `devops`
- **Área**: `docker-compose.yml` (só o serviço `banco` por enquanto), `.env.example`.
- **Fazer**: `name: kontra`; serviço `banco` com `postgres:18-alpine`, `POSTGRES_DB/USER/PASSWORD` com padrões `kontra`, porta publicada só no loopback `"127.0.0.1:${PORTA_POSTGRES:-5432}:5432"`, volume nomeado `kontra-dados-banco` montado em **`/var/lib/postgresql`** (layout do PG 18), healthcheck por TCP `pg_isready -h 127.0.0.1 -U $${POSTGRES_USER} -d $${POSTGRES_DB}`, sem `restart` policy. `.env.example` com as variáveis de banco/portas da tabela acima.
- **Pronto**:
  - `docker compose config` sem erro.
  - `docker compose up -d banco --wait` → exit 0; `docker compose ps` → `banco` `healthy`.
  - `docker compose exec banco psql -U kontra -d kontra -tc "select version()"` → `PostgreSQL 18.x`.
  - `docker compose down` e `up -d banco --wait` de novo → dados preservados (volume nomeado).
- **Depende de**: T1.

**T3 — Projeto Spring Boot base** · `backend`
- **Área**: `backend/` — `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`, `src/main/java/br/com/kontra/KontraApplication.java`, `src/main/resources/application.yml`, `src/main/resources/db/migration/V1__base_inicial.sql`, `src/test/java/br/com/kontra/suporte/TestcontainersConfig.java`, `src/test/java/br/com/kontra/KontraApplicationIT.java`.
- **Fazer**: gerar pelo Initializr (Boot 4.1.1, Java 21, Maven, `groupId` `br.com.kontra`, `artifactId` `kontra-backend`, pacote `br.com.kontra`) com `webmvc`, `data-jpa`, `validation`, `security`, `postgresql`, `flyway` (+ `flyway-database-postgresql`), `testcontainers` (`testcontainers-postgresql`, `testcontainers-junit-jupiter`, `spring-boot-testcontainers`) e os starters `*-test`. Declarar o `maven-failsafe-plugin` (goals `integration-test` e `verify`) para `*IT`. `application.yml`: datasource com `${BANCO_URL:...}`, `${BANCO_USUARIO:kontra}`, `${BANCO_SENHA:kontra}`; `spring.datasource.hikari.connection-timeout: 5000`; `spring.jpa.open-in-view: false`; `spring.jpa.hibernate.ddl-auto: validate`. `TestcontainersConfig`: `postgres:18-alpine` com `@ServiceConnection`. `KontraApplicationIT`: contexto sobe e `flyway_schema_history` contém a versão `1` com sucesso (método de teste em português, ex.: `deveAplicarMigracaoInicialAoSubir()`).
- **Pronto**:
  - `./mvnw -v` → Java 21.
  - `./mvnw verify` → `BUILD SUCCESS`; relatório do Failsafe com `KontraApplicationIT` executado (≥ 1 teste, 0 falhas).
  - `./mvnw test` (Docker desligado) → `BUILD SUCCESS` sem executar `*IT`.
  - Com `docker compose up -d banco --wait`: `./mvnw spring-boot:run` sobe na porta 8080 e o log mostra a migração `1` aplicada (ou esquema já atualizado).
- **Depende de**: T1 (commit). `spring-boot:run` usa o banco da T2.

**T4 — Formato de erro (RFC 9457) + Spring Security base** · `backend`
- **Área**: `backend/src/main/java/br/com/kontra/config/SegurancaConfig.java`, `backend/src/main/java/br/com/kontra/comum/erro/*` (`TratadorGlobalDeExcecoes`, `ExcecaoApi`, `CodigoErro`, `ProblemaResponse`, `ErroDeCampo`, `FabricaDeProblemas`, `TratadorNaoAutenticado` (401), `TratadorAcessoNegado` (403)), testes correspondentes.
- **Fazer**: conforme `docs/ARQUITETURA.md` ("Formato padronizado de erro", ADR-006, ADR-007). Rotas públicas: `GET /api/saude`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/error`; resto `authenticated()`. `STATELESS`, CSRF/httpBasic/formLogin/logout desabilitados, usuário em memória do Boot desligado. Controllers auxiliares de teste ficam só em `src/test`.
- **Pronto** (`./mvnw verify` → `BUILD SUCCESS`, com testes que cobrem):
  - `GET /api/inexistente` sem autenticação → 401, `Content-Type: application/problem+json`, `codigo` = `NAO_AUTENTICADO`, `status` = 401, `title` = "Não autenticado", `ocorridoEm` presente, sem `Set-Cookie`.
  - Corpo inválido (Bean Validation) → 400 `ERRO_VALIDACAO` com `erros[].campo` e `erros[].mensagem`.
  - JSON malformado → 400 `REQUISICAO_MALFORMADA`.
  - 4xx sem código próprio → `ERRO_REQUISICAO` com o status real (teste do `CodigoErro.deStatus` para 406 e 413, e um teste de fatia com 406 — `Accept` não atendível — verificando `status` 406 e `codigo` `ERRO_REQUISICAO`); 5xx do framework → `ERRO_INTERNO` (decisão de 2026-10-06, ver ADR-006).
  - `ExcecaoApi` → status e `codigo` informados na exceção.
  - Exceção inesperada → 500 `ERRO_INTERNO`; o corpo não contém stack trace nem nome de classe de exceção.
  - Log de inicialização sem `Using generated security password` (verificável no log do `spring-boot:run`).
- **Depende de**: T3.

**T5 — springdoc + snapshot versionado do OpenAPI** · `backend`
- **Área**: `backend/pom.xml` (`org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`), `config/OpenApiConfig.java`, `application.yml` (`springdoc.writer-with-order-by-keys: true`, `springdoc.writer-with-default-pretty-printer: true`, `springdoc.paths-to-match: /api/**`), `src/test/java/br/com/kontra/config/SnapshotOpenApiIT.java`, `backend/openapi/openapi.json`.
- **Fazer**: `OpenApiConfig` com `info` fixo (`API do Kontra`, `0.1.0`) e `servers` fixo (`/`). O `ErroPadraoController` (`/error`) **não** pode aparecer no OpenAPI nem no snapshot: usar `springdoc.paths-to-match: /api/**` (recomendado — toda a API fica sob `/api`, ADR-005; rota nova fora de `/api` não entra no contrato por engano) ou, alternativamente, `@Hidden` no controller. `SnapshotOpenApiIT` (anotado com `@TesteDeIntegracao`, ADR-011) conforme ADR-004: compara como árvore JSON; com `-Dopenapi.atualizarSnapshot=true` regrava o arquivo (JSON indentado, LF, newline final) e passa; sem a propriedade e com divergência, falha com mensagem que cita o comando de atualização. Garantir que a propriedade chega ao JVM do Failsafe.
- **Pronto**:
  - `./mvnw verify -Dopenapi.atualizarSnapshot=true` → cria `backend/openapi/openapi.json` com `info.title` = `API do Kontra` e `servers[0].url` = `/`.
  - Rodar `./mvnw verify` duas vezes seguidas → o arquivo não muda (`sha256sum backend/openapi/openapi.json` igual antes/depois).
  - Alterar `info.title` à mão no arquivo → `./mvnw verify` **falha** com a mensagem de atualização; reverter → passa.
  - O snapshot não tem o path `/error` (nem nenhum fora de `/api`): `node -e "const s=require('./backend/openapi/openapi.json');console.log(Object.keys(s.paths||{}).filter(p=>!p.startsWith('/api/')).length)"` → `0`.
  - App rodando: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/v3/api-docs` → `200`; `http://localhost:8080/swagger-ui.html` abre sem autenticação.
- **Depende de**: T4.

**T6 — Endpoint `GET /api/saude`** · `backend`
- **Área**: `saude/controller/SaudeController.java`, `saude/service/SaudeService.java`, `saude/dto/SaudeResponse.java`, `saude/dto/StatusComponente.java`, testes, `backend/openapi/openapi.json` (atualizado).
- **Fazer**: conforme o contrato acima. `SaudeController.obterSaude()` → `SaudeService.verificarBancoDeDados()`. Anotações: `@Tag(name = "saude")`, `@Operation(operationId = "obterSaude", ...)`, `@ApiResponse` para 200, 503 (`SaudeResponse`) e 500 (`ProblemaResponse`, `application/problem+json`); campos obrigatórios com `requiredMode = REQUIRED`; enums com `enumAsRef = true`.
  - Como os schemas de erro passam a aparecer no snapshot nesta tarefa, `ProblemaResponse`, `ErroDeCampo` e `CodigoErro` (`comum/erro`) recebem `@Schema`: `requiredMode = REQUIRED` em `type`, `title`, `status`, `detail`, `codigo`, `ocorridoEm` (`ProblemaResponse`) e em `campo`, `mensagem` (`ErroDeCampo`); `instance` e `erros` opcionais; `enumAsRef = true` em `CodigoErro`; descrições em português.
- **Pronto**:
  - `SaudeServiceTest` (Mockito): conexão válida → `DISPONIVEL`/`DISPONIVEL`; `SQLException` ou `isValid=false` → `INDISPONIVEL`/`INDISPONIVEL`.
  - `SaudeControllerTest` (`@WebMvcTest` + `SegurancaConfig`, service mockado): 200 com corpo `DISPONIVEL`; 503 com corpo `INDISPONIVEL`; sem autenticação; `Cache-Control: no-store`.
  - `SaudeIT` (`@TesteDeIntegracao`, Testcontainers): `GET /api/saude` → 200 e `{"status":"DISPONIVEL","bancoDeDados":"DISPONIVEL","verificadoEm":"..."}`.
  - Snapshot atualizado: `node -e "const s=require('./backend/openapi/openapi.json');console.log(s.paths['/api/saude'].get.operationId, Object.keys(s.components.schemas).sort().join(','))"` → `obterSaude CodigoErro,ErroDeCampo,ProblemaResponse,SaudeResponse,StatusComponente`; `SaudeResponse.required` contém `status`, `bancoDeDados`, `verificadoEm`; `ProblemaResponse.required` contém `type`, `title`, `status`, `detail`, `codigo`, `ocorridoEm`; `CodigoErro.enum` inclui `ERRO_REQUISICAO`.
  - Manual: com `docker compose up -d banco --wait` e `./mvnw spring-boot:run`: `curl -i http://localhost:8080/api/saude` → 200; `docker compose stop banco` → em até 10 s responde 503 com `INDISPONIVEL`; `docker compose start banco` → volta a 200.
  - `./mvnw verify` → `BUILD SUCCESS`.
- **Depende de**: T5.

**T7 — Projeto React base (Vite + TS + lint + Vitest)** · `frontend`
- **Área**: `frontend/` (exceto `Dockerfile`, `.dockerignore`, `nginx.conf`) — `package.json`, `package-lock.json`, `index.html`, `vite.config.ts`, `tsconfig*.json`, `.oxlintrc.json`, `src/main.tsx`, `src/test/configuracao.ts`, um teste de fumaça.
- **Fazer**: partir do template `react-ts` do create-vite 9; remover conteúdo de demonstração; nome `kontra-frontend`; versões do ADR-001 (TypeScript `~6.0.3`). Scripts: `dev` (`vite`), `build` (`tsc -b && vite build`), `lint` (`oxlint`), `test` (`vitest run`), `test:watch` (`vitest`), `preview`. TS `strict`. Alias `@` → `src`. Vite: `server.port` 5173, `strictPort`, proxy `/api` → `process.env.DESTINO_PROXY_API ?? 'http://localhost:8080'`. Vitest: `jsdom`, `setupFiles: src/test/configuracao.ts` (jest-dom). `.oxlintrc.json` conforme ADR-009.
- **Pronto**:
  - `npm ci` sem erro de peer dependency (sem `--legacy-peer-deps`).
  - `npm run lint` → 0 erros; incluir temporariamente `const x: any = 1` → lint falha (reverter).
  - `npm run test` → ≥ 1 teste passando; `npm run build` → gera `dist/`.
- **Depende de**: T1 (commit). **Paralela a T2–T6.**

**T8 — Casca da aplicação: MUI, React Router, TanStack Query** · `frontend`
- **Área**: `src/main.tsx`, `src/app/provedores.tsx`, `src/app/roteador.tsx`, `src/app/tema.ts`, `src/lib/cliente-query.ts`, `src/components/LayoutPrincipal.tsx`, `src/routes/PaginaInicial.tsx`, `src/routes/PaginaNaoEncontrada.tsx`, `src/test/renderizar.tsx`, testes; `package.json` (`@mui/material`, `@emotion/react`, `@emotion/styled`, `@tanstack/react-query`, `react-router`).
- **Fazer**: `ProvedoresDaAplicacao` com `ThemeProvider` + `CssBaseline` (tema com locale `ptBR`, fonte do sistema) e `QueryClientProvider` (`criarQueryClient`, defaults da ARQUITETURA); `createBrowserRouter` com `/` → `PaginaInicial` e `*` → `PaginaNaoEncontrada`. `LayoutPrincipal` com `<h1>` "Kontra". `PaginaInicial`: `<h2>` "Status do sistema" (conteúdo vem na T10). `PaginaNaoEncontrada`: título "Página não encontrada" e link "Voltar ao início" para `/`. `renderizarComProvedores` para os testes (QueryClient com `retry: false`).
- **Pronto**:
  - Testes: `/` mostra o heading "Status do sistema"; `/rota-inexistente` mostra "Página não encontrada" e o link leva a `/`.
  - `npm run lint`, `npm run test`, `npm run build` passam.
  - `npm run dev` → `http://localhost:5173` exibe a página; em 360 px de largura não há rolagem horizontal.
- **Depende de**: T7.

**T9 — Geração do cliente com orval + mutator** · `frontend`
- **Área**: `frontend/orval.config.ts`, `src/lib/cliente-api.ts` (+ `cliente-api.test.ts`), `src/api/generated/**` (gerado, versionado), `package.json` (`orval` **8.40.0 exata**, script `generate:api`: `orval`), `.oxlintrc.json` (ignorar `src/api/generated`).
- **Fazer**: conforme ADR-003/ADR-004; entrada `../backend/openapi/openapi.json` (somente leitura). `requisicaoApi` e `ErroApi` conforme a ARQUITETURA (`ErroApi.problema` tipado com o `ProblemaResponse` gerado).
- **Pronto**:
  - `npm run generate:api` → `src/api/generated/` com `useObterSaude` e `getObterSaudeQueryKey` (`grep -r "useObterSaude" src/api/generated` encontra) e os tipos `SaudeResponse`, `StatusComponente`, `ProblemaResponse`, `CodigoErro`.
  - Rodar `npm run generate:api` de novo → `git status --porcelain src/api/generated` sem alterações (após o commit) / conteúdo idêntico (antes do commit).
  - `cliente-api.test.ts`: 200 → devolve o corpo; 400 `application/problem+json` → lança `ErroApi` com `status` 400 e `problema.codigo`; 503 `application/json` → `ErroApi` com `status` 503, `problema` indefinido e `corpo` preenchido.
  - `npm run lint`, `npm run test`, `npm run build` passam.
- **Depende de**: T6, T7.

**T10 — Tela inicial exibe a verificação de saúde** · `frontend`
- **Área**: `src/features/saude/StatusDoSistema.tsx` (+ `StatusDoSistema.test.tsx`), `src/routes/PaginaInicial.tsx`.
- **Fazer**: `StatusDoSistema` usa só `useObterSaude()`. Contrato visual (usado pelo e2e):
  - carregando: elemento `role="progressbar"` com nome acessível "Verificando status do sistema";
  - sucesso: lista de definição (`<dl>`) com termos "API" e "Banco de dados"; valores "Operacional" (`DISPONIVEL`) ou "Indisponível" (`INDISPONIVEL`); "API" é "Operacional" sempre que a resposta chega; texto "Última verificação: dd/mm/aaaa hh:mm:ss" (a partir de `verificadoEm`, fuso do navegador);
  - erro (rede, 5xx — inclusive 503): `role="alert"` com "Não foi possível obter o status do sistema." e botão "Tentar novamente" que refaz a consulta.
- **Pronto**:
  - Testes (fetch simulado com corpos `satisfies SaudeResponse`; descrições em português): carregando; sucesso com dois "Operacional"; 503 → alerta; clique em "Tentar novamente" dispara nova requisição.
  - `npm run lint`, `npm run test`, `npm run build` passam.
  - Manual: `docker compose up -d banco --wait` + `./mvnw spring-boot:run` + `npm run dev` → `http://localhost:5173` mostra "Operacional" para API e Banco de dados; com o backend parado, "Tentar novamente" mantém o alerta.
- **Depende de**: T8, T9.

**T11 — Dockerfiles, nginx e compose completo** · `devops`
- **Área**: `backend/Dockerfile`, `backend/.dockerignore`, `frontend/Dockerfile`, `frontend/.dockerignore`, `frontend/nginx.conf`, `docker-compose.yml`, `.env.example`.
- **Fazer**:
  - Backend: build em `eclipse-temurin:21-jdk` (`chmod +x mvnw`, camada de dependências com `./mvnw -B dependency:go-offline`, depois `./mvnw -B -DskipTests package`); runtime `eclipse-temurin:21-jre-alpine`, usuário não root, `EXPOSE 8080`, `ENV JAVA_TOOL_OPTIONS="-Duser.language=pt -Duser.country=BR"` (locale da JVM em pt-BR para validação fora de requisição — ADR-013, "Locale fixo").
  - Frontend: build em `node:24-alpine` (`npm ci && npm run build`; o cliente gerado já está versionado); runtime `nginxinc/nginx-unprivileged:1.30-alpine` ouvindo 8080; `nginx.conf` com `location /api/ { proxy_pass http://backend:8080; ... }` (sem barra final, preserva `/api`), cabeçalhos `X-Forwarded-*`, fallback SPA `try_files $uri $uri/ /index.html`, `index.html` com `Cache-Control: no-cache`, `/assets/` com cache longo `immutable`.
  - Compose: `backend` (variáveis `BANCO_*` derivadas de `POSTGRES_*`, `depends_on: banco: service_healthy`, healthcheck `wget -qO- http://localhost:8080/api/saude`, porta `"127.0.0.1:${PORTA_BACKEND:-8080}:8080"`); `frontend` (`depends_on: backend: service_healthy`, healthcheck `wget -qO- http://localhost:8080/`, porta `"127.0.0.1:${PORTA_FRONTEND:-3000}:8080"`). Sem `restart` policy em nenhum serviço. `.env.example` completo.
  - **Atenção**: no serviço `backend`, os `BANCO_*` são montados a partir de `POSTGRES_*` com host `banco` (ex.: `BANCO_URL: jdbc:postgresql://banco:5432/${POSTGRES_DB:-kontra}`, `BANCO_USUARIO: ${POSTGRES_USER:-kontra}`, `BANCO_SENHA: ${POSTGRES_PASSWORD:-kontra}`) — **nunca** interpolar `${BANCO_URL}`/`${BANCO_USUARIO}`/`${BANCO_SENHA}` do `.env`: um `.env` copiado do exemplo (com `localhost`) quebraria o backend dentro do container.
- **Pronto**:
  - `docker compose config` sem erro; `docker compose up -d --build --wait` → exit 0; `docker compose ps` → `banco`, `backend`, `frontend` `healthy`.
  - `curl -s http://localhost:3000/api/saude` → JSON com `"status":"DISPONIVEL"`.
  - `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/qualquer/rota` → `200` (fallback SPA).
  - `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/swagger-ui/index.html` → `200`.
  - `docker compose exec backend id -u` e `docker compose exec frontend id -u` → diferentes de `0`.
  - `docker compose logs backend` mostra `Picked up JAVA_TOOL_OPTIONS: -Duser.language=pt -Duser.country=BR`.
  - Com a T10 pronta: `http://localhost:3000` mostra "Operacional" para API e Banco de dados.
  - `docker compose down`.
- **Depende de**: T2, T6, T8 (e T10 para o último item visual). **Paralela a T9/T10.**

**T12 — Projeto Playwright e e2e mínimo** · `qa`
- **Área**: `e2e/package.json` (`kontra-e2e`, `@playwright/test` 1.63.0), `e2e/package-lock.json`, `e2e/playwright.config.ts`, `e2e/tsconfig.json`, `e2e/tests/saude.spec.ts`, `e2e/tests/pagina-nao-encontrada.spec.ts`.
- **Fazer**: `baseURL` = `process.env.URL_BASE_E2E ?? 'http://localhost:3000'`; projeto único Chromium; `retries` 1 só no CI; reporters `list` + `html` (sem abrir automaticamente); `trace: 'on-first-retry'`; sem `webServer`. Seletores acessíveis, conforme o contrato visual da T10; títulos dos testes em português. Cenários mínimos:
  1. `/` exibe "Status do sistema" e "Operacional" para "API" e para "Banco de dados";
  2. `GET /api/saude` (fixture `request`, pela origem do frontend) → 200, `status` = `DISPONIVEL`;
  3. rota inexistente da SPA → "Página não encontrada"; o link "Voltar ao início" volta para `/`;
  4. `GET /api/inexistente` → 401 com `Content-Type` `application/problem+json` e `codigo` = `NAO_AUTENTICADO`.
  - Exploratório (relatado, fora da suíte): `docker compose stop banco` → a tela mostra o alerta e "Tentar novamente".
- **Pronto**: `docker compose up -d --build --wait`; `cd e2e && npm ci && npx playwright install chromium && npx playwright test` → todos passam (≥ 4); `docker compose down`.
- **Depende de**: T10, T11.

**T13 — Workflow de CI: jobs de backend e frontend** · `devops`
- **Área**: `.github/workflows/ci.yml`.
- **Fazer**: gatilhos `push` (todas as branches) e `pull_request`; `permissions: contents: read`; `concurrency` por ref com `cancel-in-progress`.
  - Job `backend` (`ubuntu-latest`, `working-directory: backend`): `actions/checkout@v7`; `actions/setup-java@v6` (Temurin 21, `cache: maven`); `./mvnw -B verify`; em falha, `actions/upload-artifact@v7` com `target/surefire-reports` e `target/failsafe-reports`.
  - Job `frontend` (`working-directory: frontend`): checkout; `actions/setup-node@v7` (Node 24, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`); `npm ci`; `npm run lint`; `npm run test`; `npm run build`; `npm run generate:api` e `git diff --exit-code -- src/api/generated`.
- **Pronto** (validação local — não há push até o usuário pedir):
  - `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/repo" -w /repo rhysd/actionlint:latest` (Git Bash, na raiz) → sem erros.
  - Num **clone limpo** da branch (`git clone --branch feat/marco-0-fundacao . <scratch>/kontra-ci`), executar no Git Bash exatamente os `run:` dos dois jobs → todos passam (prova que nada essencial ficou fora do git).
  - `git ls-files -s backend/mvnw` → modo `100755`.
  - Validação real: no primeiro push para `origin` (quando o usuário pedir), os jobs `backend` e `frontend` terminam verdes no GitHub Actions; falha vira tarefa `fix/`.
- **Depende de**: T6, T10.

**T14 — Workflow de CI: job e2e** · `devops`
- **Área**: `.github/workflows/ci.yml`.
- **Fazer**: job `e2e` com `needs: [backend, frontend]`: checkout; `docker compose up -d --build --wait`; setup-node 24 (cache de `e2e/package-lock.json`); `npm ci` em `e2e/`; `npx playwright install --with-deps chromium`; `npx playwright test`; em falha, `docker compose logs` e upload de `e2e/playwright-report`; sempre (`if: always()`) `docker compose down -v`.
- **Pronto** (validação local — não há push até o usuário pedir): actionlint sem erros; no clone limpo, a mesma sequência (no Windows, `npx playwright install chromium`, sem `--with-deps`) passa e o ambiente é derrubado ao final. Validação real: no primeiro push para `origin` (quando o usuário pedir), o job `e2e` termina verde no GitHub Actions; falha vira tarefa `fix/`.
- **Depende de**: T12, T13.

**T15 — Integração na `main` (fechamento do marco)** · `devops`
- **Fazer**: depois de revisor, qa, **seguranca** (o marco configura Spring Security) e documentador, e com aprovação do usuário: `git switch main && git merge --no-ff feat/marco-0-fundacao`.
- **Pronto**: `git log --oneline --graph -5` mostra o merge; `git status` limpo; na `main`, `./mvnw verify` (backend) e `npm run lint && npm run test && npm run build` (frontend) passam.
- **Depende de**: T1–T14 + aprovação do usuário.

---

#### Ordem e paralelismo

| Onda | Tarefas (em paralelo dentro da onda) | Pré-requisitos |
|---|---|---|
| 1 | T1 (devops) | — |
| 2 | T2 (devops) · T3 (backend) · T7 (frontend) | T1 |
| 3 | T4 (backend) · T8 (frontend) | T3 · T7 |
| 4 | T5 (backend) | T4 |
| 5 | T6 (backend) | T5 |
| 6 | T9 (frontend) · T11 (devops) | T6+T7 · T2+T6+T8 |
| 7 | T10 (frontend) | T8+T9 |
| 8 | T12 (qa) · T13 (devops) | T10+T11 · T6+T10 |
| 9 | T14 (devops) | T12+T13 |
| 10 | T15 (devops) | tudo + revisões de fim de marco + aprovação do usuário |

- **Raia backend** (serial): T3 → T4 → T5 → T6.
- **Raia frontend** (paralela à raia backend): T7 → T8; depois T9 (espera T6) → T10.
- **Raia devops** (paralela): T2 logo após T1; T11 assim que T6 e T8 estiverem prontas (em paralelo com T9/T10); T13 após T10.
- **QA**: T12 após T10 e T11; depois T14 e o fechamento (T15).
- Commits: um por tarefa, feitos pelo devops com caminhos explícitos; como backend e frontend trabalham em pastas disjuntas na mesma branch, a ordem dos commits paralelos não importa.

### Riscos

| # | Risco | Mitigação |
|---|---|---|
| R1 | O CI só roda de verdade no primeiro push para `origin` (`https://github.com/guiCoutinho/Kontra`), que só acontece quando o usuário pedir; até lá, o workflow não é exercitado no GitHub Actions. | Critério de pronto local nas T13/T14 (actionlint + mesmos comandos em clone limpo). O primeiro push é acompanhado e falhas viram tarefa `fix/`. |
| R2 | Majors recentes (Boot 4 / Spring 7 / Jackson 3 / Security 7, Vite 8, React Router 8, MUI 9, Vitest 5, TS 6) com APIs diferentes da memória dos agentes. | Consultar a documentação da versão; se bloquear, o agente avisa e propõe fixar a major anterior; o arquiteto atualiza o ADR-001. Não sobrescrever versões gerenciadas pelo Boot. |
| R3 | TypeScript 7 é o `latest` do npm e quebra peers (typescript-eslint, openapi-typescript). | `typescript ~6.0.3` fixado (ADR-001); `npm ci` sem `--legacy-peer-deps` como critério da T7. |
| R4 | Fim de linha/bit executável: `mvnw` com CRLF ou sem `+x` quebra o CI (Linux) e o build Docker. | `.gitattributes` com `eol=lf` (T1), `chmod +x mvnw` no Dockerfile, verificação `100755` na T13, clone limpo. |
| R5 | Snapshot do OpenAPI não determinístico (`servers` calculado pela requisição, ordem das chaves) → falsos positivos. | `servers`/`info` fixos, `writer-with-order-by-keys`, comparação como árvore JSON (T5). |
| R6 | Saída do orval muda entre versões → diff falso no CI. | orval com versão exata e `package-lock.json`; mudança de versão do orval = commit próprio com o cliente regenerado. |
| R7 | Testcontainers no Windows: Docker parado ou incompatível. | `./mvnw verify` exige Docker em execução (documentado); Docker 29 exige Testcontainers ≥ 2.0.2 — o Boot 4.1.1 traz 2.0.5. `./mvnw test` funciona sem Docker. |
| R8 | PostgreSQL 18 mudou o layout de dados da imagem (volume em `/var/lib/postgresql/data` não persiste corretamente). | Volume montado em `/var/lib/postgresql` (T2). |
| R9 | Conflito de portas no Windows (PostgreSQL local na 5432, outros serviços na 8080/3000). | Portas do host configuráveis (`PORTA_POSTGRES`, `PORTA_BACKEND`, `PORTA_FRONTEND`). |
| R10 | Credenciais padrão de desenvolvimento (`kontra`/`kontra`) versionadas em `application.yml`/compose; além disso, a aplicação conecta como **superusuário** (o `POSTGRES_USER` da imagem oficial é superuser). | Marcadas como só-dev (ADR-012); ambientes reais sobrescrevem por variável; portas publicadas só em `127.0.0.1`. Quando houver dados reais, criar um papel próprio da aplicação, sem superuser (dono só do esquema da aplicação). Itens da auditoria de segurança do fim do marco. |
| R11 | Swagger/OpenAPI públicos. | Aceito no Marco 0 (sem produção); desligável por variável; reavaliar no marco de autenticação. |
| R12 | A verificação de saúde (`/api/saude`) trava com o banco fora (timeout padrão do Hikari é 30 s). | `connection-timeout` 5 s + `isValid(2)`; critério manual de 503 em até 10 s (T6). |
| R13 | Agentes em paralelo na mesma árvore de trabalho misturam alterações no commit. | Propriedade por pasta (backend/frontend/devops/qa); devops adiciona arquivos por caminho explícito e confere `git diff --staged`. |
| R14 | Job e2e lento no CI (build das imagens + download do Maven/npm). | Aceito no Marco 0; cache de camadas Docker (buildx/GHA) fica para depois. |
| R15 | Erros que acontecem **antes** do Spring Security e do servlet (ex.: página 400 do Tomcat para URL com `%` solto) não seguem o contrato de erro. | `/error` próprio e tratamento das rejeições do firewall já cobrem o resto (ARQUITETURA, "Erros fora do Spring MVC"); a página do Tomcat não expõe detalhe interno. Lacuna aceita no Marco 0; tratar se surgir caso real. |
| R16 | Convenção de idioma aplicada de forma inconsistente (nomes em inglês por hábito dos agentes ou da memória de exemplos). | ADR-013 com tabela de exemplos; o revisor trata nome fora da convenção como IMPORTANTE (BLOQUEANTE quando estiver no contrato da API, pois afeta o cliente gerado). |
