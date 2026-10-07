# Status — Kontra

> Mantido pelo orquestrador. Atualizado a cada etapa.

## Marco atual

Marco 0 — Fundação (execução)

## Feito

- Etapa 1: equipe de agentes criada em `.claude/agents/`, `CLAUDE.md` e esqueleto de `docs/` (2026-10-06).
- Plano do Marco 0 elaborado pelo Arquiteto — `docs/PLANO.md` (T1–T15) e `docs/ARQUITETURA.md` (ADRs) — e aprovado pelo usuário: commit raiz na main, `br.com.kontra`, MUI, remoto `github.com/guiCoutinho/Kontra` (2026-10-06).
- Convenção de idioma definida pelo usuário: tudo em português, mantendo sufixos de camada Spring e os casos de fronteira em inglês (ADR-013, risco R16).
- **T1 concluída** (DevOps): commit raiz `55288be` na `main`, branch `feat/marco-0-fundacao`, remoto `origin` sem push. Revisor: APROVADO; QA dispensado (sem comportamento a testar; critérios conferidos pelo Revisor e pelo orquestrador).
- **T2 concluída** (DevOps): compose com PostgreSQL 18 + `.env.example` — commit `a07289e`. Revisor: APROVADO com ajustes (porta em 127.0.0.1, healthcheck TCP, sem `restart`, `.env.example` mais claro); QA: APROVADO. Plano ajustado pelo Arquiteto (T2, T11, variáveis, ADR-012, R10) — docs em `79504e7`.
- **T7 concluída** (Frontend): projeto React (Vite 8, TS 6 estrito, oxlint `--deny-warnings`, Vitest) — commit `c4e32b9`. Revisor: APROVADO com ajustes (cleanup do RTL entre testes, lint falhando em avisos, favicon próprio, `.gitignore` do template removido, tsconfig); QA: APROVADO.
- **T3 concluída** (Backend): projeto Spring Boot 4.1.1 com Flyway e Testcontainers — commit `3888064` (`mvnw` 100755); `.gitignore` de IDE do backend em `042717c`. Revisor: APROVADO (+ checksum SHA-256 da distribuição do Maven fixado); QA: APROVADO. Ressalva: "verify sem Docker deve falhar" não pôde ser simulado no Windows (Testcontainers cai no named pipe) — evidência estática de que o IT não é pulado; o CI Linux confirma.
- **T8 concluída** (Frontend): casca da aplicação com MUI 9, React Router 8 e TanStack Query 5 — commit `39b4a35`. Revisor: APROVADO com ajustes (título por página — WCAG 2.4.2, testes com `createMemoryRouter`, provedores reutilizados nos testes, teste de comportamento do retry, `errorElement` em português); QA: APROVADO (navegação real, 320/360/1280 px, teclado, console limpo). Limitação conhecida: erro lançado no próprio `LayoutPrincipal` cai na tela padrão do React Router.

- **T4 concluída** (Backend): formato de erro RFC 9457 + Spring Security base — commit `e25de6c`; docs em `3287c02`. Negar por padrão (401), sem sessão/cookie/usuário em memória; todos os erros (inclusive `/error`, firewall, TRACE) em problem+json em português, com `nosniff`/`no-store`; novo código `ERRO_REQUISICAO` (decisão do Arquiteto); Mockito como `-javaagent`. Revisor: APROVADO (2 rodadas, com testes de ataque à mão); QA: APROVADO. 88 unit/slice + 15 IT. Divergência R2 registrada: no Boot 4 usar `spring.web.error.*` (não `server.error.*`).

## Em andamento

- T5 (Backend) springdoc + snapshot versionado do OpenAPI — entregue (snapshot determinístico, falha com diff em JSON Pointer e comando de atualização, mutação sem `paths-to-match` detectada, Swagger UI público; 88 unit + 17 IT). Revisor: APROVADO (determinismo provado byte a byte, regravação acidental bloqueada, variáveis desligam o Swagger); sugestões incorporadas à T6/T13 pelo Arquiteto para evitar rodada extra. QA: APROVADO (6 adulterações do snapshot detectadas, regravação acidental bloqueada, Swagger UI ignora `?url=`/`?configUrl=` — zero requisições externas, variáveis desligam). Em commit.

## Bloqueado

- (nada)

## Notas para próximas tarefas

- T11: imagem do backend com `JAVA_TOOL_OPTIONS=-Duser.language=pt -Duser.country=BR` (validação fora de requisição usa o locale do JVM) — revisão da T4.
- T11: o nginx precisa do fallback de SPA (`try_files $uri $uri/ /index.html`) — o preview do Vite já faz, o QA da T8 confirmou o comportamento esperado.
- Acessibilidade (marco futuro): ao trocar de rota na SPA, mover o foco para o heading da página ou anunciar via `aria-live` (hoje o foco cai no `body`) — sugestão do Revisor na T8.
- Marco 1: o usuário ainda não descreveu o produto (domínio). Necessário antes de planejar o Marco 1.
- ADR-009: registrar que o lint roda com `--deny-warnings` (regras de acessibilidade bloqueiam o CI) — decisão tomada na revisão da T7.
- Auditoria de segurança (fim do marco): a aplicação conecta ao PostgreSQL como superusuário (`POSTGRES_USER` da imagem) — criar papel próprio sem superuser quando houver dados reais (R10).
- O `.gitignore` final difere um pouco da especificação da T1 em `docs/PLANO.md` (sem `build/`, `coverage/` ancorado, `.vscode` em qualquer nível, arquivos de IDE do backend), conforme a revisão.

## Histórico de marcos
