# Status — Kontra

> Mantido pelo orquestrador. Atualizado a cada etapa.

## Marco atual

**Marco 0 — Fundação: implementação concluída (T1–T14).** Falta só a **T15 — merge na `main`**, que depende da aprovação do usuário (e o push para o GitHub, também pendente de aprovação).

## Feito

- Etapa 1: equipe de agentes criada em `.claude/agents/`, `CLAUDE.md` e esqueleto de `docs/` (2026-10-06).
- Plano do Marco 0 elaborado pelo Arquiteto e aprovado pelo usuário: commit raiz na main, `br.com.kontra`, MUI, remoto `github.com/guiCoutinho/Kontra` (2026-10-06). Convenção de idioma: tudo em português (ADR-013).
- **T1** repositório + commit raiz — `55288be`.
- **T2** compose com PostgreSQL 18 + `.env.example` — `a07289e`.
- **T3** projeto Spring Boot 4.1.1 + Flyway + Testcontainers — `3888064` (+ `.gitignore` `042717c`).
- **T4** erros RFC 9457 + Spring Security base (negar por padrão) — `e25de6c`.
- **T5** springdoc + snapshot versionado do contrato OpenAPI — `9f02519`.
- **T6** `GET /api/saude` — `0e98cff`.
- **T7** projeto React (Vite 8, TS 6 estrito, oxlint, Vitest) — `c4e32b9`.
- **T8** casca do frontend (MUI 9, React Router 8, TanStack Query 5) — `39b4a35`.
- **T9** cliente da API gerado com orval — `25229ea` (1 reprovação na revisão: tipos do hook não batiam com o runtime; corrigida).
- **T10** tela de status acessível — `43355f9` (1 reprovação na revisão: regressão em refetch com dados antigos; corrigida).
- **T11** Dockerfiles, nginx e compose completo — `4f38c17`.
- **T12** e2e com Playwright (6 testes) — `cad84fa` (1 reprovação na revisão: teste dependente de tempo; corrigido com "portão").
- **T13 + T14** CI no GitHub Actions (backend, frontend, e2e) + Dependabot — `b483b5d`.
- **Auditoria de segurança** (Seguranca): 0 CRÍTICA, 0 ALTA; CVEs do Tomcat 11.0.24 e do Jackson 3.1.5/2.21.5 corrigidas com patches (11.0.26, 3.1.7, 2.21.7) + teste que impede downgrade + erro de binding sem detalhe técnico — `1e15ea3`. Reverificação: Trivy no JAR 17 → 0 vulnerabilidades.
- Documentação: `README.md`, `docs/API.md`, `CHANGELOG.md` (Documentador); `docs/ARQUITETURA.md` e `docs/PLANO.md` alinhados ao implementado (Arquiteto). Docs intermediários: `79504e7`, `3287c02`, `6fa02b3`, `f70f7d4`.

### Números finais

- Backend: 111 testes unitários/de fatia + 21 de integração (Testcontainers, PostgreSQL 18 real), snapshot OpenAPI determinístico, teste de aderência JSON real × schema.
- Frontend: 38 testes (Vitest + Testing Library), lint com `--deny-warnings`, TypeScript estrito.
- E2E: 6 testes Playwright, 6/6 contra a stack do docker compose; estáveis sob estresse (480/480 nos testes com simulação).
- CI validado localmente (actionlint + jobs backend e frontend em clone limpo com `CI=true`); execução real só após o push.

## Em andamento

- (nada) — aguardando o usuário.

## Bloqueado

- **T15 — merge na `main`**: aguarda aprovação do usuário (regra do projeto).
- **Push para `origin`** (`github.com/guiCoutinho/Kontra`): aguarda aprovação do usuário. O CI só roda de verdade depois dele (R1).

## Decisões tomadas no modo autônomo (2026-10-07, madrugada, usuário dormindo)

- Ordem das tarefas adiantada (T9, T10, T11, T12, T13, T14 e a auditoria começaram antes das dependências do plano terminarem, lendo artefatos já entregues), sempre **commitando na ordem das dependências** — sem mudança de escopo.
- Recrutados terminais extras com os mesmos agentes: **Revisor 2** (`revisor`, Opus) e **QA 2** (`qa`, Sonnet), porque revisão e QA viraram gargalo. Podem ser dispensados.
- T11: aceitos os desvios técnicos do DevOps (`unzip` no estágio de build para validar o checksum do Maven; healthcheck do frontend em `127.0.0.1`).
- T9: `allowScripts: { esbuild: false }` no `frontend/package.json` (postinstall provado desnecessário).
- **Patches de segurança sobrescrevendo versões gerenciadas pelo Spring Boot** (Tomcat 11.0.26, Jackson 3.1.7 / 2.21.7) — exceção registrada no ADR-001; remover quando o Boot gerenciar versões ≥.
- **Dependabot** adicionado no fechamento do marco (recomendação da auditoria), ignorando majors que contrariam ADRs (PostgreSQL 19, troca de LTS do Java/Node, TypeScript 7) e o mainline do nginx.
- Mensagem fixa "valor inválido" em erro de binding (sem detalhe técnico).
- Lacunas aceitas no Marco 0 (sem detalhe interno nem versão): nginx responde em HTML para 413 (corpo > 1 MB), `TRACE` e 502/504; o Tomcat rejeita no conector, com página 400 própria, `%2f`, `%00`, `%` solto e URI/cabeçalho > 8 KB.

## Para o usuário decidir

1. **Aprovar a T15** (merge `--no-ff` de `feat/marco-0-fundacao` na `main`).
2. **Aprovar o push** para `origin` (e acompanhar o primeiro run do CI).
3. **E-mail nos commits**: os 19 commits têm o seu e-mail pessoal como autor (como você pediu). Se o repositório for público, ele fica público; dá para trocar pelo e-mail noreply do GitHub e reescrever a branch **antes** do primeiro push (barato agora, caro depois).
4. **Terminais extras** (Revisor 2, QA 2): manter ou dispensar.
5. **Marco 1**: descrever o que o Kontra deve fazer (domínio), para o Arquiteto planejar.

## Notas para próximos marcos

- **Pendências de segurança** (detalhe em `docs/ARQUITETURA.md`): antes do Marco 1 — papel de banco sem superuser; antes de qualquer ambiente não-dev — sem fallback de credenciais, Swagger desligado por padrão, hardening do compose e imagens por digest, cache curto + `limit_req` no `/api/saude`, CSP/Permissions-Policy/COOP, HSTS com TLS, logs do nginx com IP mascarado (LGPD), `apk upgrade` na imagem runtime; marco de autenticação — sessão em cookie HttpOnly+Secure+SameSite com CSRF, bcrypt/Argon2, rate limit no login, IDOR com teste, `WWW-Authenticate`.
- Frontend: mensagens `typeMismatch.*` em pt-BR via `MessageSource` no primeiro marco com formulários; mover o foco para o heading ao trocar de rota na SPA; erro lançado no próprio `LayoutPrincipal` cai na tela padrão do React Router.
- CI: restringir `push` à `main` + `pull_request` quando houver PRs; cache do buildx; login no Docker Hub se aparecer `toomanyrequests`.
- O e2e só vale contra o build de produção (o StrictMode duplica a 1ª requisição em `npm run dev`).

## Histórico de marcos

- **Marco 0 — Fundação** (2026-10-06 → 2026-10-07): implementação concluída em ~6 h; 19 commits na `feat/marco-0-fundacao`; 3 reprovações em revisão (T9, T10, T12), todas resolvidas no 2º ciclo; auditoria de segurança sem achados críticos/altos após correção. Merge e push pendentes de aprovação.
