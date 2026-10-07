package br.com.kontra.comum.erro;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import br.com.kontra.suporte.TesteDeIntegracao;

/**
 * Erros que acontecem fora do Spring MVC — firewall do Spring Security, métodos não aceitos, despacho de erro
 * do contêiner e acesso direto a {@code /error} — com requisições HTTP reais ao Tomcat: todos têm de sair em
 * Problem Details, em português, sem a página "Whitelabel" nem o JSON padrão do Boot.
 */
@TesteDeIntegracao
class RespostasDeErroIT {

	private final HttpClient clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@LocalServerPort
	private int porta;

	@Autowired
	private JsonMapper jsonMapper;

	/**
	 * {@code ;}, {@code //}, {@code %2e%2e} e métodos desconhecidos são barrados pelo firewall do Spring Security
	 * ({@code TratadorRequisicaoRejeitada}). {@code TRACE} é barrado antes, pelo próprio Tomcat
	 * ({@code allowTrace=false}), com 405 encaminhado a {@code /error} ({@code ErroPadraoController}): é o caso
	 * que exercita o despacho de erro real do contêiner. O status de {@code /error} não pode ser forjado pela
	 * query string (só o contêiner define os atributos {@code jakarta.servlet.error.*}).
	 */
	@ParameterizedTest(name = "{0} {1} (Accept: {2}) → {3} {4}")
	@CsvSource({
		"GET,   /error,           application/json, 404, NAO_ENCONTRADO,        /error",
		"GET,   /error,           text/html,        404, NAO_ENCONTRADO,        /error",
		"POST,  /error,           */*,              404, NAO_ENCONTRADO,        /error",
		"PUT,   /error,           */*,              404, NAO_ENCONTRADO,        /error",
		"GET,   /error?jakarta.servlet.error.status_code=500, */*, 404, NAO_ENCONTRADO, /error",
		"GET,   /api/a;b,         */*,              400, REQUISICAO_MALFORMADA, /api/a;b",
		"GET,   /api//x,          */*,              400, REQUISICAO_MALFORMADA, /api//x",
		"GET,   /api/%2e%2e/x,    */*,              400, REQUISICAO_MALFORMADA, /api/%2e%2e/x",
		"TRACE, /api/saude,       */*,              405, METODO_NAO_PERMITIDO,  /api/saude",
		"FOO,   /api/saude,       */*,              400, REQUISICAO_MALFORMADA, /api/saude",
		"FOO,   /api/saude,       text/html,        400, REQUISICAO_MALFORMADA, /api/saude"
	})
	void deveResponderProblemJsonEmPortuguesQuandoErroAcontecerForaDoMvc(String metodo, String caminho,
			String aceito, int statusEsperado, CodigoErro codigoEsperado, String instanciaEsperada) throws Exception {
		HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + this.porta + caminho))
			.method(metodo, HttpRequest.BodyPublishers.noBody())
			.header(HttpHeaders.ACCEPT, aceito)
			.build();

		HttpResponse<String> resposta = this.clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());

		assertThat(resposta.statusCode()).as("status").isEqualTo(statusEsperado);
		assertThat(resposta.headers().firstValue(HttpHeaders.CONTENT_TYPE)).as("Content-Type")
			.hasValueSatisfying(tipo -> assertThat(MediaType.parseMediaType(tipo)
				.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).as("Content-Type %s", tipo).isTrue());
		assertThat(resposta.headers().allValues(HttpHeaders.SET_COOKIE)).as("Set-Cookie").isEmpty();
		assertThat(resposta.headers().allValues("X-Content-Type-Options")).as("X-Content-Type-Options")
			.containsExactly("nosniff");
		assertThat(resposta.headers().allValues(HttpHeaders.CACHE_CONTROL)).as("Cache-Control")
			.containsExactly("no-store");
		assertThat(resposta.body()).doesNotContainIgnoringCase("whitelabel")
			.doesNotContainIgnoringCase("spring")
			.doesNotContainIgnoringCase("timestamp")
			.doesNotContainIgnoringCase("exception")
			.doesNotContainIgnoringCase("trace\"")
			.doesNotContain("<html");

		ProblemaResponse problema = this.jsonMapper.readerFor(ProblemaResponse.class)
			.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.readValue(resposta.body());
		assertThat(problema.type()).isEqualTo(URI.create("about:blank"));
		assertThat(problema.status()).isEqualTo(statusEsperado);
		assertThat(problema.codigo()).isEqualTo(codigoEsperado);
		assertThat(problema.title()).isEqualTo(codigoEsperado.titulo());
		assertThat(problema.detail()).isNotBlank();
		assertThat(problema.instance()).isEqualTo(URI.create(instanciaEsperada));
		assertThat(problema.ocorridoEm()).isNotNull();
	}

}
