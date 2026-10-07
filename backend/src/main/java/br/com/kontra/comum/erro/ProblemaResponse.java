package br.com.kontra.comum.erro;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Formato padronizado de erro da API: Problem Details (RFC 9457) com as extensões do projeto.
 *
 * <p>Em tempo de execução, as respostas são montadas como {@link org.springframework.http.ProblemDetail}
 * pela {@link FabricaDeProblemas}; este record descreve o mesmo JSON para o contrato (schema do OpenAPI,
 * referenciado nas respostas de erro das operações) e para os testes, que verificam que o JSON real bate
 * com ele.
 *
 * @param type sempre {@code about:blank} (a semântica fina vem de {@code codigo})
 * @param title título curto em pt-BR, fixo por {@code codigo}
 * @param status código HTTP
 * @param detail mensagem em pt-BR, segura para exibir ao usuário (opcional)
 * @param instance caminho da requisição (opcional)
 * @param codigo código estável do erro
 * @param ocorridoEm momento do erro (UTC)
 * @param erros erros por campo, só em {@link CodigoErro#ERRO_VALIDACAO} (opcional)
 */
public record ProblemaResponse(URI type, String title, int status, String detail, URI instance,
		CodigoErro codigo, Instant ocorridoEm, List<ErroDeCampo> erros) {
}
