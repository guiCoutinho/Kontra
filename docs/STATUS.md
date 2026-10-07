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

## Em andamento

- T4 (Backend) formato de erro RFC 9457 + Spring Security base (+ Mockito como `-javaagent`) — entregue (49 testes unit/slice + 4 IT; 401 problem+json sem cookie; sem usuário em memória); em revisão. Arquiteto decidiu: novo `ERRO_REQUISICAO` ("Requisição não atendida") para 4xx sem código próprio, preservando o status real; registrou as decisões novas (ADR-006/007/011/013) e o `@Schema` dos erros na T6. Revisor: APROVADO com achados — `/error` e rejeições do firewall fora do contrato (Whitelabel/JSON do Boot), validação de método sem teste; sugestões: `@{argLine}`, `ExcecaoApi` só com status de erro, campo neutro `requisicao` em erros globais. Ajustes aplicados numa rodada (com o `ERRO_REQUISICAO`, `ErroPadraoController`, `TratadorRequisicaoRejeitada`): 87 unit/slice + 12 IT verdes. Re-revisão focada do delta: APROVADO, sem bloqueantes/importantes (status forjado, injeção pela URL e métodos em `/error` testados à mão). Últimas sugestões aplicadas (cabeçalhos `nosniff`/`no-store` nos erros do firewall e do `/error`, `List<@Valid ...>`, testes de `parametros` e de `/error` com POST/PUT/status forjado): 88 unit/slice + 15 IT verdes, log sem avisos. QA: APROVADO (30 combinações negadas com 401, bypass por Basic/cookie barrado, firewall e `/error` conforme contrato, página do Tomcat sem versão). Em commit. Divergência R2: no Boot 4, `server.error.*` não é lido — usar `spring.web.error.*` (Arquiteto atualizando docs).

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
