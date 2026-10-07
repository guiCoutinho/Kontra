package br.com.kontra.comum.erro;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FabricaDeProblemasTest {

	private static final Instant AGORA = Instant.parse("2026-10-06T21:00:00.123456Z");

	/** Mesmo mixin que o Boot registra no JsonMapper da aplicação. */
	private final JsonMapper jsonMapper = JsonMapper.builder()
		.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
		.build();

	private final FabricaDeProblemas fabrica = new FabricaDeProblemas(this.jsonMapper,
			Clock.fixed(AGORA, ZoneOffset.UTC));

	private final MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/api/contratos/42");

	@Test
	void deveUsarStatusTituloEDetalhePadraoQuandoSoOCodigoForInformado() {
		ProblemDetail problema = this.fabrica.criar(CodigoErro.NAO_ENCONTRADO, this.requisicao);

		assertThat(problema.getType()).isEqualTo(URI.create("about:blank"));
		assertThat(problema.getStatus()).isEqualTo(404);
		assertThat(problema.getTitle()).isEqualTo("Recurso não encontrado");
		assertThat(problema.getDetail()).isEqualTo("O recurso solicitado não foi encontrado.");
		assertThat(problema.getInstance()).isEqualTo(URI.create("/api/contratos/42"));
		assertThat(problema.getProperties()).containsEntry("codigo", CodigoErro.NAO_ENCONTRADO)
			.containsEntry("ocorridoEm", Instant.parse("2026-10-06T21:00:00.123Z"))
			.doesNotContainKey("erros");
	}

	@Test
	void deveUsarStatusEDetalheInformadosQuandoForemExplicitos() {
		ProblemDetail problema = this.fabrica.criar(HttpStatus.CONFLICT, CodigoErro.CONFLITO,
				"Já existe um contrato com esse número.", this.requisicao);

		assertThat(problema.getStatus()).isEqualTo(409);
		assertThat(problema.getTitle()).isEqualTo("Conflito");
		assertThat(problema.getDetail()).isEqualTo("Já existe um contrato com esse número.");
	}

	@Test
	void deveOrdenarErrosPorCampoQuandoCriarErroDeValidacao() {
		ProblemDetail problema = this.fabrica.criarErroDeValidacao(
				List.of(new ErroDeCampo("valor", "deve ser maior que 0"), new ErroDeCampo("nome", "não deve estar em branco")),
				this.requisicao);

		assertThat(problema.getStatus()).isEqualTo(400);
		assertThat(problema.getTitle()).isEqualTo("Dados inválidos");
		assertThat(problema.getProperties()).containsEntry("codigo", CodigoErro.ERRO_VALIDACAO)
			.containsEntry("erros", List.of(new ErroDeCampo("nome", "não deve estar em branco"),
					new ErroDeCampo("valor", "deve ser maior que 0")));
	}

	@Test
	void deveUsarCaminhoOriginalComoInstanciaQuandoForDespachoDeErro() {
		MockHttpServletRequest despachoDeErro = new MockHttpServletRequest("GET", "/error");
		despachoDeErro.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/contratos/42");

		ProblemDetail problema = this.fabrica.criar(CodigoErro.ERRO_INTERNO, despachoDeErro);

		assertThat(problema.getInstance()).isEqualTo(URI.create("/api/contratos/42"));
	}

	@Test
	void deveOmitirInstanciaQuandoCaminhoNaoFormarUriValida() {
		MockHttpServletRequest requisicaoInvalida = new MockHttpServletRequest("GET", "/api/caminho com espaço");

		ProblemDetail problema = this.fabrica.criar(CodigoErro.REQUISICAO_MALFORMADA, requisicaoInvalida);

		assertThat(problema.getInstance()).isNull();
	}

	@Test
	void deveEscreverProblemJsonComExtensoesNaRaizQuandoEscreverNaResposta() throws Exception {
		MockHttpServletResponse resposta = new MockHttpServletResponse();

		this.fabrica.escreverNaResposta(this.fabrica.criar(CodigoErro.NAO_AUTENTICADO, this.requisicao), resposta);

		assertThat(resposta.getStatus()).isEqualTo(401);
		assertThat(resposta.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		JsonNode corpo = this.jsonMapper.readTree(resposta.getContentAsByteArray());
		assertThat(corpo.get("type").asString()).isEqualTo("about:blank");
		assertThat(corpo.get("title").asString()).isEqualTo("Não autenticado");
		assertThat(corpo.get("status").asInt()).isEqualTo(401);
		assertThat(corpo.get("instance").asString()).isEqualTo("/api/contratos/42");
		assertThat(corpo.get("codigo").asString()).isEqualTo("NAO_AUTENTICADO");
		assertThat(corpo.get("ocorridoEm").asString()).isEqualTo("2026-10-06T21:00:00.123Z");
		assertThat(corpo.has("properties")).isFalse();
	}

	@Test
	void deveNaoEscreverNadaQuandoRespostaJaTiverSidoEnviada() throws Exception {
		MockHttpServletResponse resposta = new MockHttpServletResponse();
		resposta.setCommitted(true);

		this.fabrica.escreverNaResposta(this.fabrica.criar(CodigoErro.NAO_AUTENTICADO, this.requisicao), resposta);

		assertThat(resposta.getContentAsByteArray()).isEmpty();
		assertThat(resposta.getStatus()).isEqualTo(200);
	}

}
