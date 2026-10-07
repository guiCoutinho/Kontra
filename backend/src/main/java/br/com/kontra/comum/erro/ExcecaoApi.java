package br.com.kontra.comum.erro;

import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

/**
 * Base das exceções de negócio da API. Vira uma resposta Problem Details com o status, o
 * {@link CodigoErro} e a mensagem (no {@code detail}) informados.
 *
 * <p>A mensagem vai para o cliente: deve estar em pt-BR e ser segura para exibir ao usuário.
 */
public class ExcecaoApi extends RuntimeException {

	private final HttpStatus status;

	private final CodigoErro codigo;

	/**
	 * @throws IllegalArgumentException se o status não for de erro (4xx ou 5xx)
	 */
	public ExcecaoApi(HttpStatus status, CodigoErro codigo, String mensagem) {
		super(mensagem);
		Assert.isTrue(status.isError(), () -> "ExcecaoApi exige status de erro (4xx ou 5xx), recebido: " + status);
		this.status = status;
		this.codigo = codigo;
	}

	/** Usa o status usual do código (ex.: {@link CodigoErro#CONFLITO} → 409). */
	public ExcecaoApi(CodigoErro codigo, String mensagem) {
		this(codigo.status(), codigo, mensagem);
	}

	public HttpStatus status() {
		return this.status;
	}

	public CodigoErro codigo() {
		return this.codigo;
	}

}
