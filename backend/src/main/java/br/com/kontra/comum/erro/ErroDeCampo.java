package br.com.kontra.comum.erro;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Erro de validação de um campo, enviado na extensão {@code erros} do Problem Details
 * (só com {@link CodigoErro#ERRO_VALIDACAO}).
 *
 * @param campo caminho do campo inválido (ex.: {@code nome}, {@code itens[0].quantidade})
 * @param mensagem mensagem em pt-BR, segura para exibir ao usuário
 */
@Schema(description = "Erro de validação de um campo.")
public record ErroDeCampo(

		@Schema(description = "Caminho do campo inválido (ex.: nome, itens[0].quantidade). \"requisicao\" para "
				+ "erros da requisição como um todo e \"parametros\" para restrições entre parâmetros.",
				requiredMode = RequiredMode.REQUIRED)
		String campo,

		@Schema(description = "Mensagem em pt-BR, segura para exibir ao usuário.", requiredMode = RequiredMode.REQUIRED)
		String mensagem) {
}
