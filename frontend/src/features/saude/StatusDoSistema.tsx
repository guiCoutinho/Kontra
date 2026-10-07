import { useEffect, useRef } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import type { UseQueryResult } from '@tanstack/react-query'
import {
  useObterSaude,
  type ObterSaudeQueryError,
  type ObterSaudeQueryResult,
} from '@/api/generated/endpoints/saude/saude'
import { StatusComponente, type SaudeResponse } from '@/api/generated/model'

type Resultado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; tentandoNovamente: boolean }
  | { tipo: 'sucesso'; saude: SaudeResponse }

/**
 * ÚNICO ponto que conhece a forma do cliente gerado: o hook devolve a `SaudeResponse` direto em
 * `consulta.data` e qualquer erro (`ErroApi` ou falha de rede) vira `erro` — a tela não os diferencia.
 *
 * Sem dados, o TanStack Query volta o status para `pending` durante um refetch. Para a tela de erro
 * não ser trocada por um spinner (e o foco do botão não se perder), uma busca sem dados de uma
 * consulta que já falhou (`errorUpdateCount > 0`) continua sendo o erro, com `tentandoNovamente`.
 */
function lerResultado(
  consulta: Pick<
    UseQueryResult<ObterSaudeQueryResult, ObterSaudeQueryError>,
    'isError' | 'data' | 'isFetching' | 'errorUpdateCount'
  >,
): Resultado {
  // O erro vem antes dos dados: uma falha num refetch (mesmo com dados antigos) deve aparecer.
  if (consulta.isError) return { tipo: 'erro', tentandoNovamente: consulta.isFetching }
  if (consulta.data) return { tipo: 'sucesso', saude: consulta.data }
  if (consulta.isFetching && consulta.errorUpdateCount > 0) {
    return { tipo: 'erro', tentandoNovamente: true }
  }
  return { tipo: 'carregando' }
}

function formatarDataHora(iso: string): string {
  const partes = Object.fromEntries(
    new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(iso))
      .map((parte) => [parte.type, parte.value]),
  )
  return `${partes.day}/${partes.month}/${partes.year} ${partes.hour}:${partes.minute}:${partes.second}`
}

function rotuloDoStatus(status: StatusComponente): string {
  return status === StatusComponente.DISPONIVEL ? 'Operacional' : 'Indisponível'
}

function corDoStatus(status: StatusComponente): 'success.dark' | 'error.main' {
  return status === StatusComponente.DISPONIVEL ? 'success.dark' : 'error.main'
}

function ItemDeStatus({ nome, status }: { nome: string; status: StatusComponente }) {
  return (
    <>
      <Box component="dt" sx={{ fontWeight: 'bold' }}>
        {nome}
      </Box>
      <Box component="dd" sx={{ m: 0, mb: 1.5, color: corDoStatus(status) }}>
        {rotuloDoStatus(status)}
      </Box>
    </>
  )
}

interface StatusDoSistemaProps {
  /** `id` do título (h2) que nomeia a região. */
  idDoTitulo: string
}

/**
 * Região nomeada pelo título da página. Não há `role="status"`/`aria-live`: regiões ao vivo
 * aninhadas (alert dentro de status) são anunciadas em duplicidade e o refetch em segundo plano
 * releria tudo. O foco é movido explicitamente depois de "Tentar novamente":
 * - sucesso → a região (o leitor lê o nome e o conteúdo uma vez);
 * - falha → o botão (o alerta re-renderizado é anunciado pelo `role="alert"`).
 * Durante a nova tentativa o alerta continua montado e o botão fica `aria-disabled` (sem
 * `disabled`, que tiraria o foco).
 */
export function StatusDoSistema({ idDoTitulo }: StatusDoSistemaProps) {
  // Sem retry automático: a pessoa refaz pelo botão (evita espera extra com o banco fora).
  const consulta = useObterSaude({ query: { retry: false } })
  const resultado = lerResultado(consulta)
  const regiao = useRef<HTMLElement>(null)
  const botao = useRef<HTMLButtonElement>(null)
  const aguardandoFoco = useRef(false)

  const { isFetching, isError } = consulta
  useEffect(() => {
    if (!aguardandoFoco.current || isFetching) return
    aguardandoFoco.current = false
    // Só puxa o foco se a pessoa ainda está no botão (ou ele se perdeu no body): não rouba de quem foi para outro lugar.
    const ativo = document.activeElement
    if (ativo !== document.body && ativo !== botao.current) return
    ;(isError ? botao : regiao).current?.focus()
  }, [isFetching, isError])

  function tentarNovamente() {
    if (isFetching) return
    aguardandoFoco.current = true
    void consulta.refetch()
  }

  return (
    <section aria-labelledby={idDoTitulo} ref={regiao} tabIndex={-1}>
      {resultado.tipo === 'carregando' && (
        <CircularProgress aria-label="Verificando status do sistema" />
      )}

      {resultado.tipo === 'erro' && (
        <Alert
          severity="error"
          action={
            <Button
              ref={botao}
              color="inherit"
              size="small"
              aria-disabled={resultado.tentandoNovamente}
              onClick={tentarNovamente}
            >
              {resultado.tentandoNovamente ? 'Tentando…' : 'Tentar novamente'}
            </Button>
          }
        >
          Não foi possível obter o status do sistema.
        </Alert>
      )}

      {resultado.tipo === 'sucesso' && (
        <>
          <Box component="dl" sx={{ m: 0 }}>
            <ItemDeStatus nome="API" status={StatusComponente.DISPONIVEL} />
            <ItemDeStatus nome="Banco de dados" status={resultado.saude.bancoDeDados} />
          </Box>
          <Typography variant="body2" color="text.secondary">
            Última verificação: {formatarDataHora(resultado.saude.verificadoEm)}
          </Typography>
        </>
      )}
    </section>
  )
}
