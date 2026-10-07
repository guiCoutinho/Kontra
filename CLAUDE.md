# Kontra

Aplicação web em monorepo: backend Spring Boot + frontend React. Desenvolvida por uma equipe de agentes (`.claude/agents/`) coordenada por um orquestrador. Todos os agentes leem este arquivo — mantenha-o atualizado.

## Stack e convenções

- **Monorepo**: `backend/` (Spring Boot) e `frontend/` (React). Cada agente de implementação mexe só na sua pasta.
- **Backend**: Java LTS (21+), versão estável mais recente do Spring Boot, Maven, Spring Web, Spring Data JPA, Bean Validation, Spring Security, PostgreSQL, migrações com Flyway. Arquitetura em camadas (controller → service → repository), DTOs separados das entidades, tratamento global de erros com `@ControllerAdvice` e formato de erro padronizado.
- **Frontend**: React + TypeScript + Vite, TanStack Query para dados do servidor, React Router, React Hook Form + Zod para formulários, uma biblioteca de UI definida pelo arquiteto (ex.: shadcn/ui ou MUI).
- **Contrato**: OpenAPI é a fonte da verdade. O backend expõe via springdoc-openapi; o frontend gera o cliente TypeScript a partir dele (orval ou openapi-typescript). Ninguém escreve tipos de API à mão no frontend.
- **Testes**: backend com JUnit 5, Mockito, MockMvc e Testcontainers (PostgreSQL real); frontend com Vitest + React Testing Library; ponta a ponta com Playwright.
- **Ambiente**: `docker-compose.yml` na raiz sobe PostgreSQL + backend + frontend com um comando. Configuração via variáveis de ambiente, com `.env.example`.
- **CI**: GitHub Actions rodando build e testes de backend e frontend a cada push.
- **Idioma (decisão do usuário)**: tudo em português — rotas, campos JSON, tabelas, colunas, classes, métodos, variáveis, enums, textos de interface, mensagens de erro, testes, comentários, commits e documentação. Identificadores sem acento (`saude`, não `saúde`). Ficam como são apenas: palavras-chave da linguagem, APIs/anotações/arquivos impostos pelos frameworks (`application.yml`, `package.json`, `Dockerfile`, campos padrão do RFC 9457) e os sufixos de camada do vocabulário Spring (`Controller`, `Service`, `Repository`, `Dto`). Ex.: `GET /api/saude`, `SaudeController`, `verificarBancoDeDados()`, `useObterSaude()`. Detalhes no ADR correspondente em `docs/ARQUITETURA.md`.
- **Git**: uma branch por funcionalidade (`feat/<nome>`, `fix/<nome>`), nunca commit direto na `main`; Conventional Commits com escopo, mensagem em português.

Decisões específicas (versão do Spring Boot, biblioteca de UI, gerador do cliente, autenticação) ficam registradas como ADR em `docs/ARQUITETURA.md`.

## Estrutura de pastas

```
kontra/
├── .claude/agents/          # definições dos subagentes
├── .github/workflows/ci.yml # CI (GitHub Actions): jobs backend, frontend, e2e
├── backend/                 # Spring Boot 4 (Maven) — pacote br.com.kontra
│   ├── openapi/openapi.json # snapshot do contrato (gerado pelo SnapshotOpenApiIT; versionado)
│   ├── src/main/java/...    # config/, comum/erro/, <modulo>/controller|service|repository|domain|dto (ex.: saude/)
│   ├── src/main/resources/db/migration/  # migrações Flyway V<n>__descricao.sql
│   └── Dockerfile
├── frontend/                # React + TypeScript + Vite + MUI
│   ├── src/api/generated/   # cliente gerado pelo orval — nunca editar à mão; versionado
│   ├── src/{app,lib,components,features,routes,test}/  # pastas estruturais; conteúdo em português (ex.: features/saude/)
│   ├── orval.config.ts
│   └── Dockerfile, nginx.conf
├── e2e/                     # projeto Playwright (QA), roda contra o docker compose
├── docs/
│   ├── ARQUITETURA.md       # arquitetura, modelo de dados, ADRs (arquiteto)
│   ├── PLANO.md             # plano por marco: contrato, tarefas, riscos (arquiteto)
│   └── STATUS.md            # andamento (orquestrador)
├── docker-compose.yml       # serviços banco (PostgreSQL 18) + backend + frontend (nginx com proxy de /api)
├── .env.example             # todas as variáveis; .env fica fora do git
├── .gitignore / .gitattributes
├── README.md / CHANGELOG.md
└── CLAUDE.md
```

> Ainda não criado: tudo exceto `.claude/`, `docs/` e este arquivo. A estrutura é criada no Marco 0 (ver `docs/PLANO.md`).

## Comandos

> Definidos no plano do Marco 0 — confirmar ao fim do marco. No Windows, use `./mvnw` no Git Bash ou `.\mvnw.cmd` no PowerShell.

### Backend (`backend/`)

| Ação | Comando |
|---|---|
| Build + todos os testes (unit `*Test` + integração `*IT`, exige Docker) | `./mvnw verify` |
| Só testes unitários (sem Docker) | `./mvnw test` |
| Atualizar o snapshot do OpenAPI após mudar o contrato | `./mvnw verify -Dopenapi.atualizarSnapshot=true` |
| Rodar localmente (com o banco do compose: `docker compose up -d banco --wait`) | `./mvnw spring-boot:run` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Verificação de saúde | `http://localhost:8080/api/saude` |

### Frontend (`frontend/`)

| Ação | Comando |
|---|---|
| Instalar dependências | `npm ci` |
| Dev server (`http://localhost:5173`, proxy `/api` → `DESTINO_PROXY_API`, padrão `http://localhost:8080`) | `npm run dev` |
| Lint (oxlint) | `npm run lint` |
| Testes (Vitest, execução única) | `npm run test` |
| Testes em modo watch | `npm run test:watch` |
| Build (`tsc -b` + Vite) | `npm run build` |
| Gerar cliente da API (lê `../backend/openapi/openapi.json`) | `npm run generate:api` |

### Ponta a ponta (`e2e/`, com o compose no ar)

| Ação | Comando |
|---|---|
| Instalar dependências | `npm ci` |
| Instalar o navegador (primeira vez) | `npx playwright install chromium` |
| Rodar testes (`URL_BASE_E2E`, padrão `http://localhost:3000`) | `npx playwright test` |
| Ver relatório | `npx playwright show-report` |

### Docker Compose (raiz)

| Ação | Comando |
|---|---|
| Subir tudo e esperar ficar saudável | `docker compose up -d --build --wait` |
| Só o banco (para desenvolvimento local) | `docker compose up -d banco --wait` |
| Logs | `docker compose logs -f` |
| Derrubar (mantém o volume do banco) | `docker compose down` |
| Derrubar e apagar o banco | `docker compose down -v` |

URLs no compose: frontend `http://localhost:3000`, backend `http://localhost:8080`, PostgreSQL `localhost:5432` (portas configuráveis via `PORTA_FRONTEND`, `PORTA_BACKEND`, `PORTA_POSTGRES`).

Testcontainers requer o Docker em execução.

Acentos no console: o `backend/.mvn/jvm.config` força a saída do Maven/JVM em UTF-8. O Git Bash já exibe corretamente; no PowerShell/cmd rode `chcp 65001` (ou `[Console]::OutputEncoding = [Text.Encoding]::UTF8`) antes do `.\mvnw.cmd`, senão os acentos saem trocados (code page 850).

## Ambiente de desenvolvimento

Windows 10, Java 21 (Temurin), Node 24, Docker 29, Git 2.55. Shells disponíveis: PowerShell e Git Bash.

## Fluxo de trabalho

Trabalho por marcos (o Marco 0 é a fundação). Para cada tarefa: arquiteto planeja → backend/frontend implementam → revisor aprova → qa aprova → devops commita. No fim do marco: seguranca (se tocou autenticação, permissões, dados sensíveis ou pagamentos) e documentador. Status em `docs/STATUS.md`.

Os agentes rodam como terminais no canvas do Maestri (um por papel: Arquiteto, Backend, Frontend, Revisor, QA, DevOps, Seguranca, Documentador), cada um iniciado com `claude --agent <nome> --permission-mode auto` na raiz do projeto — os arquivos de `.claude/agents/` são a fonte única de prompt, modelo e ferramentas. O orquestrador delega com `maestri ask "<Nome>" "..."`; os agentes não conversam entre si.
