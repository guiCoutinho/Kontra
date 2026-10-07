# API do Kontra

Visão geral do contrato no Marco 0. A fonte da verdade é o OpenAPI (`backend/openapi/openapi.json`, título "API do Kontra", versão `0.1.0`). Toda a API fica sob `/api`.

## Swagger / OpenAPI

| Recurso | URL (backend no compose ou local) |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

Públicos, sem autenticação, no Marco 0 (ainda não há produção). Para desligar: `SPRINGDOC_API_DOCS_ENABLED=false` e `SPRINGDOC_SWAGGER_UI_ENABLED=false`. `/v3/api-docs.yaml` responde 401. A exposição deve ser reavaliada no marco de autenticação.

## Endpoints

### `GET /api/saude`

Verifica a conexão com o banco. Público. Resposta com `Cache-Control: no-store`.

| Status | Corpo | Quando |
|---|---|---|
| 200 | `SaudeResponse` com `DISPONIVEL` | Banco acessível |
| 503 | `SaudeResponse` com `INDISPONIVEL` | Banco inacessível (responde em até ~10 s) |
| 500 | `ProblemaResponse` (`ERRO_INTERNO`), `application/problem+json` | Erro inesperado |

`SaudeResponse`: `status` (agregado), `bancoDeDados` (ambos `DISPONIVEL` \| `INDISPONIVEL`) e `verificadoEm` (data-hora UTC).

```json
{ "status": "DISPONIVEL", "bancoDeDados": "DISPONIVEL", "verificadoEm": "2026-10-06T21:00:00Z" }
```

## Formato de erro

Erros usam Problem Details (RFC 9457), `Content-Type: application/problem+json`, com extensões:

| Campo | Descrição |
|---|---|
| `type`, `title`, `status`, `detail`, `instance` | Campos padrão da RFC 9457 (`title` em português, fixo por código; `instance` opcional) |
| `codigo` | Código estável do erro (tabela abaixo); o frontend decide o tratamento por ele |
| `ocorridoEm` | Data-hora UTC do erro |
| `erros` | Opcional; lista de `{ campo, mensagem }` em `ERRO_VALIDACAO` |

```json
{
  "type": "about:blank", "title": "Não autenticado", "status": 401,
  "detail": "É necessário estar autenticado para acessar este recurso.",
  "instance": "/api/inexistente", "codigo": "NAO_AUTENTICADO", "ocorridoEm": "2026-10-06T21:00:00Z"
}
```

| `codigo` | Status | Quando |
|---|---|---|
| `ERRO_VALIDACAO` | 400 | Campos inválidos (Bean Validation); detalhes em `erros` (erro de tipo/binding aparece como "valor inválido", sem detalhe técnico) |
| `REQUISICAO_MALFORMADA` | 400 | Corpo ilegível, método desconhecido ou URL rejeitada pelo firewall do Spring Security |
| `NAO_AUTENTICADO` | 401 | Rota que exige autenticação sem credenciais |
| `ACESSO_NEGADO` | 403 | Autenticado sem permissão |
| `NAO_ENCONTRADO` | 404 | Recurso inexistente |
| `METODO_NAO_PERMITIDO` | 405 | Método HTTP não permitido |
| `TIPO_DE_MIDIA_NAO_SUPORTADO` | 415 | Tipo de conteúdo não suportado |
| `CONFLITO` | 409 | Conflito com o estado do recurso |
| `ERRO_REQUISICAO` | 4xx real | Qualquer outro 4xx (ex.: 406, 413); exibir o `detail` |
| `ERRO_INTERNO` | 5xx | Erro inesperado; sem stack trace nem nome de classe no corpo |

## Segurança

- Spring Security nega por padrão: só são públicos `GET /api/saude`, a documentação (`/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`) e `/error`.
- Qualquer outra rota, inclusive inexistente, responde **401** `NAO_AUTENTICADO`. Isso vale também para outros métodos em `/api/saude` (ex.: `POST`, `HEAD`).
- Sem sessão, cookies, CSRF, HTTP Basic ou login: **ainda não há autenticação** (será definida em marco futuro).
- `TRACE` responde 405 `METODO_NAO_PERMITIDO` (`application/problem+json`) **direto no backend** (`:8080`). Pelo frontend/nginx (`:3000`), o próprio nginx responde 405 em HTML (ver "Lacunas aceitas").

## Lacunas aceitas

Respostas fora do contrato (páginas HTML/texto, sem Problem Details):

- Tomcat, antes de chegar à aplicação: barra codificada (`%2f`), `%00`, `%` solto, URI ou cabeçalho acima de 8 KB → 400 no formato do próprio Tomcat.
- nginx do frontend, em erros próprios: 413 (corpo grande), `TRACE`, 502/504 com o backend fora.

Pendências de segurança dos próximos marcos (Swagger desligado por padrão, cabeçalhos no nginx, papel de banco sem superuser etc.) estão em `docs/ARQUITETURA.md`, seção "Pendências de segurança".
