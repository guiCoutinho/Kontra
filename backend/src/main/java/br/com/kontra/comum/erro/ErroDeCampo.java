package br.com.kontra.comum.erro;

/**
 * Erro de validação de um campo, enviado na extensão {@code erros} do Problem Details
 * (só com {@link CodigoErro#ERRO_VALIDACAO}).
 *
 * @param campo caminho do campo inválido (ex.: {@code nome}, {@code itens[0].quantidade})
 * @param mensagem mensagem em pt-BR, segura para exibir ao usuário
 */
public record ErroDeCampo(String campo, String mensagem) {
}
