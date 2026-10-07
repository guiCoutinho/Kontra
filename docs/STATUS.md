# Status — Kontra

> Mantido pelo orquestrador. Atualizado a cada etapa.

## Marco atual

Marco 0 — Fundação (execução)

## Feito

- Etapa 1: equipe de agentes criada em `.claude/agents/`, `CLAUDE.md` e esqueleto de `docs/` (2026-10-06).
- Marco 0: plano elaborado pelo Arquiteto — `docs/PLANO.md` (T1–T15, riscos R1–R15) e `docs/ARQUITETURA.md` (ADR-001…012) (2026-10-06).
- Marco 0: plano aprovado pelo usuário — commit raiz na main, `br.com.kontra`, MUI, remoto `github.com/guiCoutinho/Kontra` (2026-10-06).
- Arquiteto registrou as decisões do usuário (ADR-002 e ADR-010 aceitos; T1, T13, T14 e R1 ajustados).
- T1 parte 1 (DevOps): git init, `.gitignore`, `.gitattributes`, remoto `origin` — Revisor: APROVADO.

- Convenção de idioma definida pelo usuário: tudo em português, mantendo sufixos de camada Spring (`CLAUDE.md`).

- Arquiteto aplicou a convenção de idioma em `docs/` e `CLAUDE.md` (ADR-013, risco R16). Usuário confirmou os casos de fronteira em inglês (pacotes de camada/config, `KontraApplication`, pastas estruturais do frontend, scripts npm, nomes impostos por terceiros).

- **T1 concluída** (DevOps): commit raiz `55288be` na `main` (14 arquivos), branch `feat/marco-0-fundacao`, remoto `origin` sem push. Revisor: APROVADO; QA dispensado (sem comportamento a testar; critérios conferidos pelo Revisor e pelo orquestrador).

## Em andamento

- Onda 2 (em paralelo):
  - T2 (DevOps) compose com PostgreSQL — Revisor: APROVADO com ajustes (porta em 127.0.0.1, healthcheck TCP, sem `restart`, `.env.example` mais claro); ajustes aplicados pelo DevOps; plano ajustado pelo Arquiteto (T2, T11, variáveis, ADR-012, R10). QA: APROVADO (porta só no loopback, 1ª subida estável em 3 ciclos, overrides, persistência, entradas inválidas). Em commit.
  - T3 (Backend) projeto Spring Boot — entregue (`./mvnw verify` OK, V1 aplicada); na fila do Revisor.
  - T7 (Frontend) projeto React — entregue, em revisão.

## Bloqueado

- (nada)

## Notas para próximas tarefas

- T4: avaliar `-javaagent` do Mockito no argLine (aviso de self-attaching no JDK 21+), apontado pelo Backend na T3.
- T3 (commit): `backend/mvnw` precisa entrar com `git add --chmod=+x` e `git ls-files -s backend/mvnw` → `100755` (a máquina usa `core.filemode=false`).
- Auditoria de segurança (fim do marco): a aplicação conecta ao PostgreSQL como superusuário (`POSTGRES_USER` da imagem) — criar papel próprio sem superuser quando houver dados reais (R10).
- O `.gitignore` final difere um pouco da especificação da T1 em `docs/PLANO.md` (sem `build/`, `coverage/` ancorado, `.vscode` em qualquer nível, arquivos de IDE do backend), conforme a revisão.

## Histórico de marcos
