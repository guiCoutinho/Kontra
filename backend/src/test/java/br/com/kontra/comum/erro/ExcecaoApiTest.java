package br.com.kontra.comum.erro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.http.HttpStatus;

class ExcecaoApiTest {

	@ParameterizedTest(name = "{0}")
	@EnumSource(value = HttpStatus.class, names = { "CONTINUE", "OK", "CREATED", "NO_CONTENT", "FOUND", "NOT_MODIFIED" })
	void deveRecusarQuandoStatusNaoForDeErro(HttpStatus status) {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ExcecaoApi(status, CodigoErro.CONFLITO, "Mensagem qualquer."))
			.withMessageContaining("status de erro");
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(value = HttpStatus.class, names = { "BAD_REQUEST", "CONFLICT", "UNPROCESSABLE_CONTENT",
			"INTERNAL_SERVER_ERROR", "SERVICE_UNAVAILABLE" })
	void deveAceitarQuandoStatusForDeErro(HttpStatus status) {
		ExcecaoApi excecao = new ExcecaoApi(status, CodigoErro.CONFLITO, "Já existe um registro com esse nome.");

		assertThat(excecao.status()).isEqualTo(status);
		assertThat(excecao.codigo()).isEqualTo(CodigoErro.CONFLITO);
		assertThat(excecao.getMessage()).isEqualTo("Já existe um registro com esse nome.");
	}

	@Test
	void deveUsarStatusUsualDoCodigoQuandoStatusNaoForInformado() {
		ExcecaoApi excecao = new ExcecaoApi(CodigoErro.NAO_ENCONTRADO, "O contrato 42 não foi encontrado.");

		assertThat(excecao.status()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
