# Kontra

Fundação de uma aplicação web em monorepo. O domínio de negócio ainda será definido; por ora o projeto entrega a infraestrutura pronta para desenvolver e uma tela de status que consulta a saúde do backend.

**Stack**: backend Spring Boot 4 (Java 21, Maven, JPA, Flyway, Spring Security, springdoc) com PostgreSQL 18; frontend React 19 + TypeScript + Vite + MUI + TanStack Query + React Router; testes com JUnit/Testcontainers, Vitest e Playwright; CI no GitHub Actions.

```
backend/   API Spring Boot (pacote br.com.kontra) + snapshot do contrato em openapi/openapi.json
frontend/  SPA React; cliente da API gerado em src/api/generated/
e2e/       testes Playwright (rodam contra o docker compose)
docs/      ARQUITETURA.md (ADRs), PLANO.md, STATUS.md, API.md
```

## Pré-requisitos

- Java 21 (Temurin) — o Maven vem pelo wrapper (`mvnw`)
- Node 24 e npm
- Docker (obrigatório para o compose e para os testes de integração do backend, que usam Testcontainers)
- Windows: Git Bash ou PowerShell (`./mvnw` no Git Bash, `.\mvnw.cmd` no PowerShell). No PowerShell/cmd rode `chcp 65001` antes do Maven, senão os acentos saem trocados.

## Rodar com docker compose

```bash
docker compose up -d --build --wait   # banco + backend + frontend, espera ficarem saudáveis
```

| Serviço | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| Saúde (pelo frontend) | http://localhost:3000/api/saude |
| PostgreSQL | localhost:5432 |

O frontend (nginx) repassa `/api` ao backend. Todas as portas são publicadas só em `127.0.0.1`.

Comandos úteis: `docker compose logs -f`, `docker compose down` (mantém o banco), `docker compose down -v` (apaga o banco).

### Variáveis (`.env`)

Copie `.env.example` para `.env` (fora do git) para mudar os padrões. **Só o docker compose lê o `.env`**:

| Variável | Padrão | Uso |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `kontra` | Banco (senha só de desenvolvimento). Mudar depois da primeira subida exige `docker compose down -v` |
| `PORTA_POSTGRES`, `PORTA_BACKEND`, `PORTA_FRONTEND` | `5432`, `8080`, `3000` | Portas no host |

As demais variáveis do `.env.example` são só referência e **não** vêm do `.env` — exporte-as no shell (Git Bash: `export NOME=valor`; PowerShell: `$env:NOME="valor"`) ou configure na IDE:

| Variável | Padrão | Uso |
|---|---|---|
| `BANCO_URL`, `BANCO_USUARIO`, `BANCO_SENHA` | `jdbc:postgresql://localhost:5432/kontra`, `kontra`, `kontra` | Backend rodando fora do compose (no compose são montadas a partir de `POSTGRES_*`) |
| `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED` | `true` | Desligam OpenAPI e Swagger UI |
| `DESTINO_PROXY_API` | `http://localhost:8080` | Destino do proxy `/api` no `npm run dev` |
| `URL_BASE_E2E` | `http://localhost:3000` | URL do frontend nos testes e2e |

## Rodar localmente (sem containers de app)

```bash
docker compose up -d banco --wait     # só o PostgreSQL
cd backend && ./mvnw spring-boot:run  # API em http://localhost:8080
cd frontend && npm ci && npm run dev  # SPA em http://localhost:5173, com proxy de /api
```

Se mudar `PORTA_POSTGRES` ou `PORTA_BACKEND`, ajuste `BANCO_URL` e `DESTINO_PROXY_API` no shell.

## Testes

| Onde | Comando |
|---|---|
| Backend — tudo (unitários `*Test` + integração `*IT`, exige Docker) | `cd backend && ./mvnw verify` |
| Backend — só unitários (sem Docker) | `./mvnw test` |
| Frontend | `cd frontend && npm run lint && npm run test && npm run build` |
| e2e (com o compose no ar) | `cd e2e && npm ci && npx playwright install chromium && npx playwright test` |

O e2e usa `URL_BASE_E2E` (padrão `http://localhost:3000`); checagem de tipos com `npm run typecheck`; relatório com `npx playwright show-report`. São 6 testes (tela de status, erro e recuperação simulados com `page.route`, rota inexistente da SPA e `GET /api/saude`/401 pela origem do frontend).

> **O e2e só vale contra o build de produção** (compose em `:3000` ou `vite preview`). No `npm run dev` o `React.StrictMode` duplica a primeira requisição e os testes que contam chamadas falham.

## Contrato da API e cliente gerado

O OpenAPI é a fonte da verdade. O snapshot versionado fica em `backend/openapi/openapi.json`; o teste `SnapshotOpenApiIT` falha se o contrato real divergir dele.

1. Mudou a API? Atualize o snapshot: `cd backend && ./mvnw verify -Dopenapi.atualizarSnapshot=true` (recusado quando a variável `CI` está definida) e commite o arquivo.
2. Regenere o cliente TypeScript: `cd frontend && npm run generate:api` (orval, lê `../backend/openapi/openapi.json`) e commite.
3. **Nunca edite `frontend/src/api/generated/` à mão.**

Versões de segurança fixadas no `backend/pom.xml` (Tomcat 11.0.26, Jackson 3.1.7 / 2.21.7, por patch de CVE até o Spring Boot gerenciá-las; guardadas por `VersoesMinimasDeSegurancaTest`): não rebaixe ao atualizar o Boot.

Detalhes dos endpoints e do formato de erro em [`docs/API.md`](docs/API.md).

## CI

`.github/workflows/ci.yml` roda no GitHub Actions a cada push, pull request ou manualmente:

- **backend**: `./mvnw verify` (unitários + integração) e checagem de que `openapi/openapi.json` não mudou.
- **frontend**: `npm ci`, lint, testes, build e checagem de que `src/api/generated` está atualizado após `npm run generate:api`.
- **e2e** (depois dos dois anteriores): sobe o compose com `docker compose up -d --build --wait`, instala o e2e, faz a checagem de tipos, instala o Chromium (com cache), roda o Playwright com `URL_BASE_E2E=http://127.0.0.1:3000` e derruba tudo; em falha, anexa relatório e logs.

O `.github/dependabot.yml` abre PRs semanais (segundas, 06:00 BRT) de dependências Maven, npm (frontend e e2e), Docker, docker-compose e GitHub Actions; ignora majors que contrariam os ADRs. PR que atualiza o `orval` exige `npm run generate:api` (senão o CI falha).

## Convenções

- Tudo em português: rotas, campos, tabelas, classes, textos, testes, commits e documentação (identificadores sem acento).
- Git: uma branch por funcionalidade (`feat/<nome>`, `fix/<nome>`), nunca commit direto na `main`; Conventional Commits com escopo e mensagem em português.
- Arquitetura e decisões (ADRs): [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) · plano por marco: [`docs/PLANO.md`](docs/PLANO.md) · andamento: [`docs/STATUS.md`](docs/STATUS.md) · pendências de segurança: seção "Pendências de segurança" de `docs/ARQUITETURA.md` · mudanças: [`CHANGELOG.md`](CHANGELOG.md).
