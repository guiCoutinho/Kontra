package br.com.kontra.comum.erro;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Código estável de erro, enviado na extensão {@code codigo} do Problem Details (RFC 9457).
 *
 * <p>O frontend decide o tratamento pelo código; o {@code title} da resposta é fixo por código.
 */
@Schema(enumAsRef = true, description = "Código estável do erro. Códigos genéricos (ERRO_REQUISICAO, ERRO_INTERNO) "
		+ "devem ser tratados exibindo o detail.")
public enum CodigoErro {

	ERRO_VALIDACAO(HttpStatus.BAD_REQUEST, "Dados inválidos", "A requisição contém campos inválidos."),

	REQUISICAO_MALFORMADA(HttpStatus.BAD_REQUEST, "Requisição malformada",
			"A requisição não pôde ser lida. Verifique o formato dos dados enviados."),

	NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Não autenticado",
			"É necessário estar autenticado para acessar este recurso."),

	ACESSO_NEGADO(HttpStatus.FORBIDDEN, "Acesso negado", "Você não tem permissão para acessar este recurso."),

	NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso não encontrado", "O recurso solicitado não foi encontrado."),

	METODO_NAO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido",
			"O método HTTP usado não é permitido para este recurso."),

	TIPO_DE_MIDIA_NAO_SUPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de mídia não suportado",
			"O tipo de conteúdo enviado não é suportado por este recurso."),

	CONFLITO(HttpStatus.CONFLICT, "Conflito", "A operação conflita com o estado atual do recurso."),

	/** Qualquer 4xx sem código próprio (ex.: 406, 413); a resposta mantém o status HTTP real. */
	ERRO_REQUISICAO(HttpStatus.BAD_REQUEST, "Requisição não atendida", "A requisição não pôde ser atendida."),

	ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
			"Ocorreu um erro inesperado. Tente novamente mais tarde.");

	private final HttpStatus status;

	private final String titulo;

	private final String detalhePadrao;

	CodigoErro(HttpStatus status, String titulo, String detalhePadrao) {
		this.status = status;
		this.titulo = titulo;
		this.detalhePadrao = detalhePadrao;
	}

	/** Status HTTP usual deste código. */
	public HttpStatus status() {
		return this.status;
	}

	/** Título curto em pt-BR, usado no campo {@code title}. */
	public String titulo() {
		return this.titulo;
	}

	/** Mensagem em pt-BR usada no {@code detail} quando não há uma mais específica. */
	public String detalhePadrao() {
		return this.detalhePadrao;
	}

	/**
	 * Código correspondente a um status HTTP de erro, para erros do framework e do contêiner que não têm
	 * tratamento específico (ADR-006): 400 → {@link #REQUISICAO_MALFORMADA}; 401, 403, 404, 405, 409 e 415 →
	 * o código próprio; qualquer outro 4xx (ex.: 406, 413, 414, 431) → {@link #ERRO_REQUISICAO}; 5xx (e
	 * qualquer status que não seja 4xx) → {@link #ERRO_INTERNO}. A resposta sempre mantém o status real.
	 */
	public static CodigoErro deStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> REQUISICAO_MALFORMADA;
			case 401 -> NAO_AUTENTICADO;
			case 403 -> ACESSO_NEGADO;
			case 404 -> NAO_ENCONTRADO;
			case 405 -> METODO_NAO_PERMITIDO;
			case 409 -> CONFLITO;
			case 415 -> TIPO_DE_MIDIA_NAO_SUPORTADO;
			default -> status.is4xxClientError() ? ERRO_REQUISICAO : ERRO_INTERNO;
		};
	}

}
