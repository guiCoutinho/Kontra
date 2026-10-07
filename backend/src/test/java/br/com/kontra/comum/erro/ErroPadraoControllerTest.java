package br.com.kontra.comum.erro;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import br.com.kontra.config.SegurancaConfig;

/**
 * {@code /error} chamado diretamente e simulando o despacho de erro do contêiner (atributos
 * {@code jakarta.servlet.error.*}); o despacho real é coberto pelo {@code RespostasDeErroIT}.
 */
@WebMvcTest(ErroPadraoController.class)
@Import(SegurancaConfig.class)
class ErroPadraoControllerTest {

	private static final String INSTANTE_ISO_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deveResponderNaoEncontradoQuandoErrorForAcessadoDiretamente() throws Exception {
		this.mockMvc.perform(get("/error"))
			.andExpectAll(problema(404, CodigoErro.NAO_ENCONTRADO, "/error"),
					jsonPath("$.detail").value("O recurso solicitado não foi encontrado."));
	}

	@Test
	void deveResponderProblemJsonSemPaginaWhitelabelQuandoClientePedirHtml() throws Exception {
		this.mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML))
			.andExpectAll(problema(404, CodigoErro.NAO_ENCONTRADO, "/error"),
					content().string(not(containsStringIgnoringCase("whitelabel"))),
					content().string(not(containsStringIgnoringCase("<html"))));
	}

	@ParameterizedTest(name = "status {0} → {1}")
	@CsvSource({ "400, REQUISICAO_MALFORMADA", "401, NAO_AUTENTICADO", "404, NAO_ENCONTRADO",
			"405, METODO_NAO_PERMITIDO", "413, ERRO_REQUISICAO", "500, ERRO_INTERNO", "503, ERRO_INTERNO" })
	void deveUsarStatusECaminhoDoDespachoQuandoConteinerEncaminharErro(int status, CodigoErro codigo)
			throws Exception {
		this.mockMvc
			.perform(post("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
				.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/contratos/42")
				.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("detalhe interno secreto"))
				.accept(MediaType.TEXT_HTML))
			.andExpectAll(problema(status, codigo, "/api/contratos/42"),
					jsonPath("$.detail").value(codigo.detalhePadrao()),
					content().string(not(containsStringIgnoringCase("secreto"))),
					content().string(not(containsStringIgnoringCase("exception"))),
					content().string(not(containsStringIgnoringCase("timestamp"))));
	}

	@Test
	void deveResponderErroInternoQuandoStatusDoDespachoNaoForDeErro() throws Exception {
		this.mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 200))
			.andExpectAll(problema(500, CodigoErro.ERRO_INTERNO, "/error"));
	}

	private static ResultMatcher problema(int status, CodigoErro codigo, String instancia) {
		return todos(status().is(status), content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
				header().string("X-Content-Type-Options", "nosniff"), header().string(HttpHeaders.CACHE_CONTROL, "no-store"),
				jsonPath("$.type").value("about:blank"), jsonPath("$.status").value(status),
				jsonPath("$.title").value(codigo.titulo()), jsonPath("$.codigo").value(codigo.name()),
				jsonPath("$.instance").value(instancia), jsonPath("$.ocorridoEm").value(matchesPattern(INSTANTE_ISO_UTC)));
	}

	/** Aplica todos os comparadores, na ordem ({@code ResultMatcher.matchAll} não existe mais no Spring 7). */
	private static ResultMatcher todos(ResultMatcher... comparadores) {
		return resultado -> {
			for (ResultMatcher comparador : comparadores) {
				comparador.match(resultado);
			}
		};
	}

}
