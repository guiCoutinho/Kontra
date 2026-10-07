package br.com.kontra.comum.erro;

import static br.com.kontra.suporte.ErrosDeTesteController.CAMINHO_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import br.com.kontra.config.SegurancaConfig;
import br.com.kontra.suporte.ErrosDeTesteController;

/**
 * Cenários de erro do Spring MVC, com a {@link SegurancaConfig} real e um usuário autenticado (as rotas do
 * controller de teste não são públicas).
 */
@WebMvcTest(ErrosDeTesteController.class)
@Import({ SegurancaConfig.class, ErrosDeTesteController.class })
@WithMockUser
class TratadorGlobalDeExcecoesTest {

	private static final String INSTANTE_ISO_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void deveResponderErroDeValidacaoComErrosPorCampoQuandoCorpoForInvalido() throws Exception {
		this.mockMvc
			.perform(post(CAMINHO_BASE + "/corpo").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\": \"  \", \"quantidade\": 0}"))
			.andExpectAll(problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO, CAMINHO_BASE + "/corpo"),
					jsonPath("$.title").value("Dados inválidos"),
					jsonPath("$.detail").value("A requisição contém campos inválidos."),
					jsonPath("$.erros.length()").value(2),
					jsonPath("$.erros[0].campo").value("nome"),
					jsonPath("$.erros[0].mensagem").value("não deve estar em branco"),
					jsonPath("$.erros[1].campo").value("quantidade"),
					jsonPath("$.erros[1].mensagem").value("deve ser maior que 0"));
	}

	@Test
	void deveResponderErroDeValidacaoComNomeDoParametroQuandoParametroForInvalido() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/parametro").param("valor", " "))
			.andExpectAll(problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO, CAMINHO_BASE + "/parametro"),
					jsonPath("$.erros.length()").value(1),
					jsonPath("$.erros[0].campo").value("valor"),
					jsonPath("$.erros[0].mensagem").value("não deve estar em branco"));
	}

	@Test
	void deveResponderErroDeValidacaoQuandoHouverViolacaoDeRestricao() throws Exception {
		this.mockMvc
			.perform(post(CAMINHO_BASE + "/violacao-de-restricao").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\": \"Kontra\"}"))
			.andExpectAll(
					problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO, CAMINHO_BASE + "/violacao-de-restricao"),
					jsonPath("$.erros.length()").value(1),
					jsonPath("$.erros[0].campo").value("quantidade"),
					jsonPath("$.erros[0].mensagem").value("não deve ser nulo"));
	}

	@Test
	void deveExtrairCampoSemMetodoEParametroQuandoValidacaoDeMetodoFalhar() throws Exception {
		// Caminhos de origem: criar.requisicao.itens[0].quantidade, criar.requisicao.nome, criar.valor.
		this.mockMvc.perform(post(CAMINHO_BASE + "/validacao-de-metodo"))
			.andExpectAll(
					problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO, CAMINHO_BASE + "/validacao-de-metodo"),
					jsonPath("$.erros.length()").value(3),
					jsonPath("$.erros[0].campo").value("itens[0].quantidade"),
					jsonPath("$.erros[0].mensagem").value("deve ser maior que 0"),
					jsonPath("$.erros[1].campo").value("nome"),
					jsonPath("$.erros[1].mensagem").value("não deve estar em branco"),
					jsonPath("$.erros[2].campo").value("valor"),
					jsonPath("$.erros[2].mensagem").value("deve ser maior que 0"));
	}

	@Test
	void deveUsarCampoParametrosQuandoRestricaoEntreParametrosFalhar() throws Exception {
		this.mockMvc.perform(post(CAMINHO_BASE + "/validacao-entre-parametros"))
			.andExpectAll(
					problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO,
							CAMINHO_BASE + "/validacao-entre-parametros"),
					jsonPath("$.erros.length()").value(1), jsonPath("$.erros[0].campo").value("parametros"),
					jsonPath("$.erros[0].mensagem").value("o fim deve ser maior que o início"));
	}

	@Test
	void deveUsarCampoNeutroSemNomeDaClasseQuandoRestricaoDeClasseFalharNoCorpo() throws Exception {
		this.mockMvc
			.perform(post(CAMINHO_BASE + "/restricao-de-classe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\": 5, \"fim\": 1}"))
			.andExpectAll(
					problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO, CAMINHO_BASE + "/restricao-de-classe"),
					jsonPath("$.erros.length()").value(1),
					jsonPath("$.erros[0].campo").value("requisicao"),
					jsonPath("$.erros[0].mensagem").value("o fim deve ser posterior ao início"),
					content().string(not(containsStringIgnoringCase("periodoDeTesteRequest"))));
	}

	@Test
	void deveUsarCampoNeutroQuandoRestricaoDeClasseGerarViolacaoSemCaminho() throws Exception {
		this.mockMvc
			.perform(post(CAMINHO_BASE + "/violacao-de-restricao-de-classe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\": 5, \"fim\": 1}"))
			.andExpectAll(
					problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO,
							CAMINHO_BASE + "/violacao-de-restricao-de-classe"),
					jsonPath("$.erros.length()").value(1), jsonPath("$.erros[0].campo").value("requisicao"),
					jsonPath("$.erros[0].mensagem").value("o fim deve ser posterior ao início"));
	}

	@Test
	void deveResponderErroDeRequisicaoComStatusRealQuandoAcceptNaoPuderSerAtendido() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/ok").accept(MediaType.APPLICATION_XML))
			.andExpectAll(problema(HttpStatus.NOT_ACCEPTABLE, CodigoErro.ERRO_REQUISICAO, CAMINHO_BASE + "/ok"),
					jsonPath("$.title").value("Requisição não atendida"),
					jsonPath("$.detail").value("O formato de resposta solicitado não está disponível para este recurso."));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "/filtro", "/filtro-com-validacao-de-metodo" })
	void deveUsarMensagemFixaEmPortuguesQuandoBindingDeModelAttributeFalharPorTipoInvalido(String rota) throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + rota).param("pagina", "abc").param("nome", "Kontra"))
			.andExpectAll(problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO, CAMINHO_BASE + rota),
					jsonPath("$.erros.length()").value(1), jsonPath("$.erros[0].campo").value("pagina"),
					jsonPath("$.erros[0].mensagem").value("valor inválido"),
					content().string(not(containsString("java."))),
					content().string(not(containsStringIgnoringCase("Failed to convert"))),
					content().string(not(containsStringIgnoringCase("typeMismatch"))));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "/filtro", "/filtro-com-validacao-de-metodo" })
	void deveManterMensagemDaRestricaoQuandoBindingFuncionarMasValidacaoFalhar(String rota) throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + rota).param("pagina", "0").param("nome", " "))
			.andExpectAll(problema(HttpStatus.BAD_REQUEST, CodigoErro.ERRO_VALIDACAO, CAMINHO_BASE + rota),
					jsonPath("$.erros.length()").value(2), jsonPath("$.erros[0].campo").value("nome"),
					jsonPath("$.erros[0].mensagem").value("não deve estar em branco"),
					jsonPath("$.erros[1].campo").value("pagina"),
					jsonPath("$.erros[1].mensagem").value("deve ser maior que 0"));
	}

	@Test
	void deveResponderRequisicaoMalformadaSemDetalhesDoParserQuandoJsonForInvalido() throws Exception {
		this.mockMvc
			.perform(post(CAMINHO_BASE + "/corpo").contentType(MediaType.APPLICATION_JSON).content("{\"nome\": "))
			.andExpectAll(problema(HttpStatus.BAD_REQUEST, CodigoErro.REQUISICAO_MALFORMADA, CAMINHO_BASE + "/corpo"),
					jsonPath("$.title").value("Requisição malformada"),
					jsonPath("$.detail").value("A requisição não pôde ser lida. Verifique o formato dos dados enviados."),
					jsonPath("$.erros").doesNotExist(),
					content().string(not(containsStringIgnoringCase("jackson"))),
					content().string(not(containsStringIgnoringCase("exception"))));
	}

	@Test
	void deveResponderRequisicaoMalformadaQuandoParametroTiverTipoInvalido() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/numero").param("valor", "abc"))
			.andExpectAll(problema(HttpStatus.BAD_REQUEST, CodigoErro.REQUISICAO_MALFORMADA, CAMINHO_BASE + "/numero"),
					jsonPath("$.detail").value("O parâmetro 'valor' tem um valor inválido."));
	}

	@Test
	void deveResponderRequisicaoMalformadaQuandoParametroObrigatorioFaltar() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/numero"))
			.andExpectAll(problema(HttpStatus.BAD_REQUEST, CodigoErro.REQUISICAO_MALFORMADA, CAMINHO_BASE + "/numero"),
					jsonPath("$.detail").value("O parâmetro obrigatório 'valor' não foi informado."));
	}

	@Test
	void deveUsarStatusCodigoEMensagemDaExcecaoQuandoExcecaoApiForLancada() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/excecao-api"))
			.andExpectAll(problema(HttpStatus.CONFLICT, CodigoErro.CONFLITO, CAMINHO_BASE + "/excecao-api"),
					jsonPath("$.title").value("Conflito"),
					jsonPath("$.detail").value("Já existe um registro com esse nome."));
	}

	@Test
	void deveUsarStatusUsualDoCodigoQuandoExcecaoApiNaoInformarStatus() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/excecao-api-com-status-padrao"))
			.andExpectAll(
					problema(HttpStatus.NOT_FOUND, CodigoErro.NAO_ENCONTRADO,
							CAMINHO_BASE + "/excecao-api-com-status-padrao"),
					jsonPath("$.detail").value("O contrato 42 não foi encontrado."));
	}

	@Test
	void deveResponderErroInternoSemStackTraceNemMensagemInternaQuandoExcecaoForInesperada() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/excecao-inesperada"))
			.andExpectAll(
					problema(HttpStatus.INTERNAL_SERVER_ERROR, CodigoErro.ERRO_INTERNO,
							CAMINHO_BASE + "/excecao-inesperada"),
					jsonPath("$.title").value("Erro interno"),
					jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente mais tarde."),
					jsonPath("$.trace").doesNotExist(), jsonPath("$.exception").doesNotExist(),
					content().string(not(containsString("IllegalStateException"))),
					content().string(not(containsString("java."))),
					content().string(not(containsString("br.com.kontra"))),
					content().string(not(containsString("segredo123"))),
					content().string(not(containsString("SELECT"))),
					content().string(not(containsString(ErrosDeTesteController.MENSAGEM_INTERNA_SENSIVEL))));
	}

	@Test
	void deveResponderNaoEncontradoQuandoRotaAutenticadaNaoExistir() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/inexistente"))
			.andExpectAll(problema(HttpStatus.NOT_FOUND, CodigoErro.NAO_ENCONTRADO, CAMINHO_BASE + "/inexistente"),
					jsonPath("$.title").value("Recurso não encontrado"));
	}

	@Test
	void deveResponderMetodoNaoPermitidoComCabecalhoAllowQuandoMetodoNaoForSuportado() throws Exception {
		this.mockMvc.perform(delete(CAMINHO_BASE + "/ok"))
			.andExpectAll(problema(HttpStatus.METHOD_NOT_ALLOWED, CodigoErro.METODO_NAO_PERMITIDO, CAMINHO_BASE + "/ok"),
					jsonPath("$.detail").value("O método DELETE não é permitido para este recurso."),
					header().string("Allow", containsString("GET")));
	}

	@Test
	void deveResponderTipoDeMidiaNaoSuportadoQuandoContentTypeNaoForAceito() throws Exception {
		this.mockMvc.perform(post(CAMINHO_BASE + "/corpo").contentType(MediaType.TEXT_PLAIN).content("texto"))
			.andExpectAll(problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CodigoErro.TIPO_DE_MIDIA_NAO_SUPORTADO,
					CAMINHO_BASE + "/corpo"), header().exists("Accept"));
	}

	@Test
	void deveResponderProblemJsonQuandoClienteAceitarApenasJson() throws Exception {
		this.mockMvc.perform(get(CAMINHO_BASE + "/excecao-api").accept(MediaType.APPLICATION_JSON))
			.andExpectAll(problema(HttpStatus.CONFLICT, CodigoErro.CONFLITO, CAMINHO_BASE + "/excecao-api"));
		this.mockMvc.perform(get(CAMINHO_BASE + "/numero").accept(MediaType.APPLICATION_JSON))
			.andExpectAll(problema(HttpStatus.BAD_REQUEST, CodigoErro.REQUISICAO_MALFORMADA, CAMINHO_BASE + "/numero"));
		this.mockMvc.perform(get(CAMINHO_BASE + "/excecao-inesperada").accept(MediaType.APPLICATION_JSON))
			.andExpectAll(problema(HttpStatus.INTERNAL_SERVER_ERROR, CodigoErro.ERRO_INTERNO,
					CAMINHO_BASE + "/excecao-inesperada"));
	}

	@Test
	void deveSeguirExatamenteOContratoProblemaResponseQuandoResponderErro() throws Exception {
		String corpo = this.mockMvc
			.perform(post(CAMINHO_BASE + "/corpo").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\": \"\", \"quantidade\": 1}"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		// Falha se o JSON tiver qualquer campo fora do contrato (ex.: "properties", "trace").
		ProblemaResponse problema = this.jsonMapper.readerFor(ProblemaResponse.class)
			.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.readValue(corpo);

		assertThat(problema.type()).isEqualTo(URI.create("about:blank"));
		assertThat(problema.title()).isEqualTo("Dados inválidos");
		assertThat(problema.status()).isEqualTo(400);
		assertThat(problema.detail()).isEqualTo("A requisição contém campos inválidos.");
		assertThat(problema.instance()).isEqualTo(URI.create(CAMINHO_BASE + "/corpo"));
		assertThat(problema.codigo()).isEqualTo(CodigoErro.ERRO_VALIDACAO);
		assertThat(problema.ocorridoEm()).isNotNull();
		assertThat(problema.erros()).isEqualTo(List.of(new ErroDeCampo("nome", "não deve estar em branco")));
	}

	/** Campos obrigatórios do formato de erro (ver {@link ProblemaResponse}). */
	private static ResultMatcher problema(HttpStatus status, CodigoErro codigo, String instancia) {
		return todos(status().is(status.value()),
				content().contentType(MediaType.APPLICATION_PROBLEM_JSON), jsonPath("$.type").value("about:blank"),
				jsonPath("$.title").value(codigo.titulo()), jsonPath("$.status").value(status.value()),
				jsonPath("$.detail").isString(), jsonPath("$.instance").value(instancia),
				jsonPath("$.codigo").value(codigo.name()), jsonPath("$.ocorridoEm").value(matchesPattern(INSTANTE_ISO_UTC)));
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
