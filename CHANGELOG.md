# Changelog

## Marco 0 — Fundação

### Adicionado
- Monorepo com `backend/` (Spring Boot 4, Java 21, Maven, Flyway, migração inicial `V1`), `frontend/` (React 19, Vite, TypeScript, MUI, TanStack Query, React Router) e `e2e/` (Playwright).
- `GET /api/saude`: verificação do banco (200 `DISPONIVEL` / 503 `INDISPONIVEL`).
- Tela inicial "Status do sistema" (região acessível) com a saúde da API e do banco pelo cliente gerado; em erro mostra alerta e "Tentar novamente".
- OpenAPI com springdoc e Swagger UI (`/swagger-ui.html`); snapshot versionado em `backend/openapi/openapi.json`.
- Cliente TypeScript gerado pelo orval a partir do snapshot (`npm run generate:api`).
- Formato de erro padronizado (RFC 9457 + `codigo`, `ocorridoEm`, `erros`).

### Segurança
- Spring Security negando por padrão (401 `NAO_AUTENTICADO`), sem sessão/CSRF/login; só `GET /api/saude`, documentação e `/error` são públicos.
- Correção de CVEs: Tomcat 11.0.26 e Jackson 3.1.7/2.21.7 fixados no `pom.xml`, com teste que impede rebaixar.
- Erro de binding responde "valor inválido", sem detalhe técnico.
- Auditoria de segurança: 0 crítica/alta; pendências registradas em `docs/ARQUITETURA.md`.
- Portas do compose publicadas só em `127.0.0.1`; containers de backend e frontend sem root.

### Infra
- `docker compose up -d --build --wait` sobe PostgreSQL 18, backend e frontend (nginx com proxy de `/api`); variáveis em `.env.example`.
- CI no GitHub Actions: jobs `backend`, `frontend` (com checagem do cliente gerado) e `e2e` (typecheck, cache do Playwright).
- Dependabot semanal (Maven, npm, Docker, compose, Actions).

### Testes
- Backend: unitários, fatia web e integração com Testcontainers; teste de snapshot do contrato.
- Frontend: Vitest + Testing Library.
- e2e: 6 testes Playwright contra o build de produção (inclui erro e recuperação determinísticos).
- Teste de versões mínimas de segurança (`VersoesMinimasDeSegurancaTest`).
