package br.com.kontra.config;

import static br.com.kontra.suporte.ErrosDeTesteController.CAMINHO_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import br.com.kontra.comum.erro.CodigoErro;
import br.com.kontra.suporte.ErrosDeTesteController;

/** Regras de acesso da {@link SegurancaConfig} e respostas 401/403 no formato Problem Details. */
@WebMvcTest(ErrosDeTesteController.class)
@Import({ SegurancaConfig.class, ErrosDeTesteController.class })
class SegurancaConfigTest {

	private static final String INSTANTE_ISO_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deveResponderNaoAutenticadoSemCriarSessaoQuandoRotaInexistenteForAcessadaSemAutenticacao() throws Exception {
		MvcResult resultado = this.mockMvc.perform(get("/api/inexistente"))
			.andExpectAll(naoAutenticado("/api/inexistente"),
					jsonPath("$.detail").value("É necessário estar autenticado para acessar este recurso."))
			.andReturn();

		assertThat(resultado.getRequest().getSession(false)).as("sessão HTTP criada").isNull();
	}

	@Test
	void deveResponderNaoAutenticadoQuandoRotaProtegidaForAcessadaSemAutenticacao() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/ok")).andExpectAll(naoAutenticado(CAMINHO_BASE + "/ok"));
	}

	@ParameterizedTest(name = "{0} /api/saude")
	@ValueSource(strings = { "POST", "PUT", "PATCH", "DELETE" })
	void deveExigirAutenticacaoQuandoMetodoDaRotaDeSaudeNaoForGet(String metodo) throws Exception {
		this.mockMvc.perform(request(HttpMethod.valueOf(metodo), "/api/saude"))
			.andExpectAll(naoAutenticado("/api/saude"));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({ "/api/saude", "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html",
			"/swagger-ui/index.html", "/error" })
	void deveLiberarRotaPublicaQuandoNaoHouverAutenticacao(String caminho) throws Exception {
		int status = this.mockMvc.perform(get(caminho)).andReturn().getResponse().getStatus();

		assertThat(status).as("status de GET %s", caminho).isNotIn(401, 403);
	}

	@Test
	void deveIgnorarCredenciaisHttpBasicQuandoHttpBasicEstiverDesabilitado() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/ok").with(httpBasic("usuario", "senha")))
			.andExpectAll(naoAutenticado(CAMINHO_BASE + "/ok"));
	}

	@ParameterizedTest(name = "{0} {1}")
	@CsvSource({ "GET, /login", "POST, /login", "POST, /logout" })
	void deveNaoOferecerLoginNemLogoutQuandoEstiveremDesabilitados(String metodo, String caminho) throws Exception {
		this.mockMvc.perform(request(HttpMethod.valueOf(metodo), caminho)).andExpectAll(naoAutenticado(caminho));
	}

	/**
	 * Firewall do Spring Security ({@code TratadorRequisicaoRejeitada}). No MockMvc não há Tomcat, então o
	 * {@code TRACE} chega ao firewall; num servidor real o Tomcat o barra antes, com 405 (ver
	 * {@code RespostasDeErroIT}).
	 */
	@ParameterizedTest(name = "{0} {1}")
	@CsvSource({ "GET, /api/a;b", "GET, /api//x", "GET, /api/%2e%2e/x", "TRACE, /api/saude", "FOO, /api/saude" })
	void deveResponderRequisicaoMalformadaQuandoFirewallRejeitarRequisicao(String metodo, String caminho)
			throws Exception {
		this.mockMvc.perform(request(HttpMethod.valueOf(metodo), URI.create(caminho)))
			.andExpectAll(status().isBadRequest(), content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
					jsonPath("$.type").value("about:blank"), jsonPath("$.status").value(400),
					jsonPath("$.title").value("Requisição malformada"),
					jsonPath("$.codigo").value(CodigoErro.REQUISICAO_MALFORMADA.name()),
					jsonPath("$.detail")
						.value("A requisição foi rejeitada por conter caminho, caracteres ou método HTTP não permitidos."),
					jsonPath("$.instance").value(caminho), jsonPath("$.ocorridoEm").value(matchesPattern(INSTANTE_ISO_UTC)),
					header().doesNotExist(HttpHeaders.SET_COOKIE), header().string("X-Content-Type-Options", "nosniff"),
					header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
	}

	@Test
	@WithMockUser
	void devePermitirAcessoQuandoUsuarioEstiverAutenticado() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/ok"))
			.andExpectAll(status().isOk(), jsonPath("$.resultado").value("ok"));
	}

	@Test
	@WithMockUser
	void deveResponderAcessoNegadoQuandoUsuarioAutenticadoNaoTiverPermissao() throws Exception {
		MvcResult resultado = this.mockMvc.perform(get(CAMINHO_BASE + "/acesso-negado"))
			.andExpectAll(status().isForbidden(), content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
					jsonPath("$.type").value("about:blank"), jsonPath("$.status").value(403),
					jsonPath("$.title").value("Acesso negado"), jsonPath("$.codigo").value("ACESSO_NEGADO"),
					jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."),
					jsonPath("$.instance").value(CAMINHO_BASE + "/acesso-negado"),
					jsonPath("$.ocorridoEm").value(matchesPattern(INSTANTE_ISO_UTC)),
					header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andReturn();

		assertThat(resultado.getRequest().getSession(false)).as("sessão HTTP criada").isNull();
	}

	private static ResultMatcher naoAutenticado(String instancia) {
		return todos(status().isUnauthorized(),
				content().contentType(MediaType.APPLICATION_PROBLEM_JSON), jsonPath("$.type").value("about:blank"),
				jsonPath("$.status").value(401), jsonPath("$.title").value("Não autenticado"),
				jsonPath("$.codigo").value(CodigoErro.NAO_AUTENTICADO.name()), jsonPath("$.instance").value(instancia),
				jsonPath("$.ocorridoEm").value(matchesPattern(INSTANTE_ISO_UTC)),
				header().doesNotExist(HttpHeaders.SET_COOKIE), header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
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
