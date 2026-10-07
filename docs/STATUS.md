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

- Arquiteto aplicou a convenção de idioma em `docs/` e `CLAUDE.md` (ADR-013, risco R16).

## Em andamento

- T1 parte 2 (DevOps): ajustes do `.gitignore`/`.gitattributes` sugeridos pelo Revisor, identidade git local, commit raiz na `main`, branch `feat/marco-0-fundacao`.

## Bloqueado

- (nada)

## Notas para próximas tarefas

- T3 (commit): `backend/mvnw` precisa entrar com `git add --chmod=+x` e `git ls-files -s backend/mvnw` → `100755` (a máquina usa `core.filemode=false`).
- O `.gitignore` final difere um pouco da especificação da T1 em `docs/PLANO.md` (sem `build/`, `coverage/` ancorado, `.vscode` em qualquer nível, arquivos de IDE do backend), conforme a revisão.

## Histórico de marcos
