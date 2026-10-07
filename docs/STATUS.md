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
- **T5 concluída** (Backend): springdoc 3.1.1 + snapshot versionado `backend/openapi/openapi.json` — commit `9f02519`; docs em `6fa02b3`. Snapshot determinístico (provado byte a byte), falha com diff e comando de atualização, regravação só com `-D` explícito, apenas rotas `/api/**`, Swagger UI público e desligável. Revisor: APROVADO; QA: APROVADO. Sugestões incorporadas à T6/T13.

## Em andamento

- T6 (Backend) `GET /api/saude` + `@Schema` dos erros + proteções extras do snapshot (aderência JSON × schema, recusa de regravação no CI, `override-with-generic-response: false`, `.mvn/jvm.config` UTF-8) — entregue (98 unit/slice + 21 IT; 503 em 5 s com banco fora; teste de aderência JSON real × schema pegou as mutações, inclusive a lacuna Jackson 2×3; recusa no CI; acentos OK). Revisor: APROVADO (contrato conferido; teste de carga: 1000 req/200 paralelas sem esgotar o pool; com banco fora as demais rotas seguem em ~2 ms). Ajustes finais aplicados: `format: uri-reference` em `instance`/`type` (snapshot `5f955aee…`), WARN só na transição de estado (sem mensagem do driver no log; 8 threads → 1 WARN), teste de `RuntimeException`; acentos no PowerShell exigem `chcp 65001` (documentado no `CLAUDE.md`). 103 unit + 21 IT. QA: APROVADO (503 em 5,2 s; 1 WARN do serviço na queda e 1 INFO na volta; 50 paralelas OK; proteções do snapshot). Em commit. Observação: na 1ª falha o próprio Hikari loga ~10 WARN de validação de conexão (ruído do framework).
- T10 (Frontend) adiantada: tela `StatusDoSistema` — entregue (23 testes; `dl > dt + dd`; acesso ao envelope/erro do cliente isolado em `lerResultado()` — o isolamento funcionou quando a T9 mudou). Revisor: APROVADO (estrutura, contraste AA, fuso, 360 px verificados no navegador) com 1 importante de acessibilidade: após "Tentar novamente" o foco cai no `body` e o resultado não é anunciado. Corrigida: região `role="status"` sempre montada recebe o foco após "Tentar novamente"; `retry: false` na tela de saúde; TZ fixo nos testes (provado); fetch simulado no `roteador.test.tsx`; 34 testes. Re-revisão focada em andamento. Atenção para o e2e: `getByRole('term', { name })` pode não achar o `<dt>`.
- T13 (DevOps) adiantada: `.github/workflows/ci.yml` (jobs backend e frontend; actions fixadas por SHA; actionlint OK) — parte 1: Revisor APROVADO (SHAs conferidos, `git diff` relativo pega divergência real); sugestões aplicadas (checagem de arquivos novos não rastreados, `cancel-in-progress` fora da main, `workflow_dispatch`, `-ntp`; actionlint OK). Validação em clone limpo após os commits de T6/T9/T10.
- T9 (Frontend) adiantada: cliente gerado pelo orval a partir do snapshot da T6 — entregue (geração determinística, 15 testes, retry com `instanceof ErroApi`); Revisor 2: **REPROVADO (1º ciclo)** — os tipos do hook gerado (dados e erro) não correspondem ao runtime (envelope com união sucesso|erro que nunca acontece; `TError` ≠ `ErroApi`). Correção por configuração do orval 8.40 (`includeHttpResponseReturnType: false` + `export type ErrorType` no mutator), testes do 200/502/Accept, retry só rede/5xx, `allowScripts` do esbuild negado. Corrigida pelo Frontend (30 testes; `obterSaude(): Promise<SaudeResponse>`; `TError = ErrorType<...>`; `prettier: false` inválido removido do `orval.config.ts`) — Revisor 2: **APROVADO no 2º ciclo** (tipos do hook provados iguais ao runtime com asserções exatas e controle negativo; `npm ci` limpo também em `node:24-alpine`). Arquiteto registrou as decisões (ADR-001/003/013). QA (QA 2, cópia isolada): APROVADO (gerado idêntico ao real; divergências de contrato quebram o `tsc` onde devem; 18 cenários HTTP reais; regra de retry; chamada ao backend real). Pronta para commit, depois do da T6 (o gerado depende do snapshot da T6).
- T11 (DevOps) adiantada: Dockerfiles, nginx e compose completo — entregue; stack completa healthy em projeto isolado (`/api/saude` via nginx = DISPONIVEL, usuários não root, locale pt-BR). Desvios: `unzip` no estágio de build (validação do checksum do Maven) e healthcheck do frontend em `127.0.0.1` (nginx só IPv4) — o Revisor confirmou que são as melhores saídas. Revisor: APROVADO com 4 importantes (nginx resolvia `backend` só na partida → 502 ao recriar o backend; SPA sem cabeçalhos de segurança; `server_tokens`; `.env` fora dos `.dockerignore`) + sugestões (`absolute_redirect off`, jar só leitura, gzip). Ajustes aplicados (cabeçalhos de segurança só nos locations estáticos, para não duplicar os do backend no `/api`); recriar o backend não gera mais 502. QA: APROVADO na stack principal (loopback, resiliência, cabeçalhos, `.env` não vaza para o bundle, e2e 4/4 passou). Ajuste final aplicado: healthcheck do backend com `timeout` 10 s, `retries` 3, `start_period` 60 s (banco fora → unhealthy em 30 s, antes ~3,5 min); estáticos 644/755 na imagem. Pronta para commit, depois da T6. Lacuna aceita: nginx responde 413 em HTML (fora do problem+json) para corpo > 1 MB.
- T12 (QA) adiantada: projeto Playwright pronto (4 testes, `tsc` OK, página não encontrada passando contra o dev server) — execução completa contra a stack após T10/T11; seletor do `<dd>` "Operacional" a confirmar com a estrutura real da T10.

> Modo autônomo a partir de 2026-10-07 ~00:10 (usuário dormindo): o orquestrador segue sem aguardar respostas; decisões tomadas sem o usuário ficam registradas em "Decisões tomadas no modo autônomo". Merge na `main` (T15) e push ficam pendentes de aprovação.

## Bloqueado

- (nada)

## Decisões tomadas no modo autônomo

- T11 e T12 adiantadas (antes da T6/T10) para reduzir o tempo total — mesma equipe, sem mudança de escopo.
- Recrutado um segundo terminal de revisão ("Revisor 2", mesmo agente `revisor`, Opus) porque a fila de revisão virou o gargalo (T11, T6 e T9 esperando ao mesmo tempo). Pode ser dispensado quando o usuário quiser.
- Recrutado um segundo terminal de QA ("QA 2", mesmo agente `qa`, Sonnet) porque a fila de QA virou o gargalo (T6, T9 e T10 ao mesmo tempo). Pode ser dispensado quando o usuário quiser.
- T9 iniciada antes da revisão da T6 (o Revisor estava na T11), lendo o snapshot entregue; o commit da T9 só sai depois do da T6.
- T11: aceitos os desvios técnicos do DevOps (`unzip` no build; healthcheck do frontend em `127.0.0.1`), sujeitos à revisão.

## Notas para próximas tarefas

- T11: imagem do backend com `JAVA_TOOL_OPTIONS=-Duser.language=pt -Duser.country=BR` (validação fora de requisição usa o locale do JVM) — revisão da T4.
- Auditoria/futuro (revisão da T6): a saúde usa o mesmo pool das rotas de negócio — coalescer a verificação com cache curto (1–2 s) para não segurar uma thread por 5 s com o banco fora e evitar falso negativo sob carga; `limit_req` no nginx para `/api/saude` quando houver exposição real.
- Futuro (T13): quando houver PRs, restringir `push` à `main` + `pull_request` (hoje cada push num PR do próprio repositório roda duas vezes).
- Futuro (T13): `dependabot.yml` para `github-actions` (as actions estão fixadas por SHA e precisam de atualização periódica).
- Futuro (revisão da T11): fixar imagens base por digest (com Dependabot), cache do BuildKit (`--mount=type=cache`), `$http_host` + `server.forward-headers-strategy` se o backend precisar gerar URLs absolutas; Content-Security-Policy no nginx.
- T11: o nginx precisa do fallback de SPA (`try_files $uri $uri/ /index.html`) — o preview do Vite já faz, o QA da T8 confirmou o comportamento esperado.
- Acessibilidade (marco futuro): ao trocar de rota na SPA, mover o foco para o heading da página ou anunciar via `aria-live` (hoje o foco cai no `body`) — sugestão do Revisor na T8.
- Marco 1: o usuário ainda não descreveu o produto (domínio). Necessário antes de planejar o Marco 1.
- ADR-009: registrar que o lint roda com `--deny-warnings` (regras de acessibilidade bloqueiam o CI) — decisão tomada na revisão da T7.
- Auditoria de segurança (fim do marco): a aplicação conecta ao PostgreSQL como superusuário (`POSTGRES_USER` da imagem) — criar papel próprio sem superuser quando houver dados reais (R10).
- O `.gitignore` final difere um pouco da especificação da T1 em `docs/PLANO.md` (sem `build/`, `coverage/` ancorado, `.vscode` em qualquer nível, arquivos de IDE do backend), conforme a revisão.

## Histórico de marcos
