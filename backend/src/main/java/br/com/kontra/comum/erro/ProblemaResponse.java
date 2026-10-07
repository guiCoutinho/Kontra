package br.com.kontra.comum.erro;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Formato padronizado de erro da API: Problem Details (RFC 9457) com as extensões do projeto.
 *
 * <p>Em tempo de execução, as respostas são montadas como {@link org.springframework.http.ProblemDetail}
 * pela {@link FabricaDeProblemas}; este record descreve o mesmo JSON para o contrato (schema do OpenAPI,
 * referenciado nas respostas de erro das operações) e para os testes, que verificam que o JSON real bate
 * com ele.
 *
 * <p>{@code type} e {@code instance} são "URI reference" (RFC 9457): no schema, {@code format: uri-reference}, pois
 * {@code format: uri} exigiria URI absoluta e o {@code instance} real é relativo ({@code /api/saude}).
 *
 * @param type sempre {@code about:blank} (a semântica fina vem de {@code codigo})
 * @param title título curto em pt-BR, fixo por {@code codigo}
 * @param status código HTTP real da resposta
 * @param detail mensagem em pt-BR, segura para exibir ao usuário
 * @param instance caminho da requisição (opcional)
 * @param codigo código estável do erro
 * @param ocorridoEm momento do erro (UTC)
 * @param erros erros por campo, só em {@link CodigoErro#ERRO_VALIDACAO} (opcional)
 */
@Schema(description = "Erro no formato Problem Details (RFC 9457), com as extensões do projeto "
		+ "(codigo, ocorridoEm, erros). Content-Type: application/problem+json.")
public record ProblemaResponse(

		@Schema(description = "Tipo do problema (referência de URI, RFC 9457). Sempre about:blank: a semântica fina "
				+ "vem de codigo.", format = "uri-reference", requiredMode = RequiredMode.REQUIRED)
		URI type,

		@Schema(description = "Título curto em pt-BR, fixo por codigo.", requiredMode = RequiredMode.REQUIRED)
		String title,

		@Schema(description = "Código HTTP real da resposta.", requiredMode = RequiredMode.REQUIRED)
		int status,

		@Schema(description = "Mensagem em pt-BR, segura para exibir ao usuário.", requiredMode = RequiredMode.REQUIRED)
		String detail,

		@Schema(description = "Caminho da requisição (referência de URI relativa, ex.: /api/saude; omitido se não "
				+ "formar uma URI válida).", format = "uri-reference")
		URI instance,

		@Schema(description = "Código estável do erro, para o cliente decidir o tratamento.",
				requiredMode = RequiredMode.REQUIRED)
		CodigoErro codigo,

		@Schema(description = "Momento do erro (UTC, precisão de milissegundos).", requiredMode = RequiredMode.REQUIRED)
		Instant ocorridoEm,

		@Schema(description = "Erros por campo, ordenados por campo e mensagem. Só com codigo ERRO_VALIDACAO.")
		List<ErroDeCampo> erros) {
}
