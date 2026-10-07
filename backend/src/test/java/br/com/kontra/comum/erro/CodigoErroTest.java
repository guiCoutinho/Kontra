package br.com.kontra.comum.erro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.http.HttpStatusCode;

class CodigoErroTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
		"400, REQUISICAO_MALFORMADA",
		"401, NAO_AUTENTICADO",
		"403, ACESSO_NEGADO",
		"404, NAO_ENCONTRADO",
		"405, METODO_NAO_PERMITIDO",
		"406, ERRO_REQUISICAO",
		"409, CONFLITO",
		"410, ERRO_REQUISICAO",
		"413, ERRO_REQUISICAO",
		"414, ERRO_REQUISICAO",
		"415, TIPO_DE_MIDIA_NAO_SUPORTADO",
		"422, ERRO_REQUISICAO",
		"431, ERRO_REQUISICAO",
		"500, ERRO_INTERNO",
		"502, ERRO_INTERNO",
		"503, ERRO_INTERNO"
	})
	void deveEscolherCodigoPeloStatusQuandoExcecaoNaoTiverTratamentoProprio(int status, CodigoErro esperado) {
		assertThat(CodigoErro.deStatus(HttpStatusCode.valueOf(status))).isEqualTo(esperado);
	}

	@Test
	void deveUsarTituloEDetalheGenericosQuandoCodigoForErroDeRequisicao() {
		assertThat(CodigoErro.ERRO_REQUISICAO.titulo()).isEqualTo("Requisição não atendida");
		assertThat(CodigoErro.ERRO_REQUISICAO.detalhePadrao()).isEqualTo("A requisição não pôde ser atendida.");
	}

}
