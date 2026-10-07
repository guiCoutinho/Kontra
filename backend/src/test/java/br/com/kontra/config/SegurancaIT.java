package br.com.kontra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;

import br.com.kontra.comum.erro.CodigoErro;
import br.com.kontra.comum.erro.FabricaDeProblemas;
import br.com.kontra.comum.erro.ProblemaResponse;
import br.com.kontra.suporte.TesteDeIntegracao;

/** Ponta a ponta, com a aplicação completa num Tomcat real e requisições HTTP de verdade. */
@TesteDeIntegracao
class SegurancaIT {

	private final HttpClient clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@LocalServerPort
	private int porta;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private ApplicationContext contexto;

	@Test
	void deveResponderNaoAutenticadoEmProblemJsonSemCookieQuandoRotaInexistenteForAcessada() throws Exception {
		Instant antes = Instant.now().minusSeconds(1);

		HttpResponse<String> resposta = this.clienteHttp.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + this.porta + "/api/inexistente")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resposta.statusCode()).isEqualTo(401);
		assertThat(resposta.headers().firstValue(HttpHeaders.CONTENT_TYPE))
			.hasValueSatisfying(tipo -> assertThat(MediaType.parseMediaType(tipo).isCompatibleWith(
					MediaType.APPLICATION_PROBLEM_JSON)).as("Content-Type %s", tipo).isTrue());
		assertThat(resposta.headers().allValues(HttpHeaders.SET_COOKIE)).isEmpty();

		ProblemaResponse problema = this.jsonMapper.readerFor(ProblemaResponse.class)
			.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.readValue(resposta.body());
		assertThat(problema.type()).isEqualTo(URI.create("about:blank"));
		assertThat(problema.status()).isEqualTo(401);
		assertThat(problema.title()).isEqualTo("Não autenticado");
		assertThat(problema.codigo()).isEqualTo(CodigoErro.NAO_AUTENTICADO);
		assertThat(problema.instance()).isEqualTo(URI.create("/api/inexistente"));
		assertThat(problema.ocorridoEm()).isAfter(antes).isBeforeOrEqualTo(Instant.now());
		assertThat(problema.erros()).isNull();
	}

	@Test
	void deveSubirSemUsuarioEmMemoriaQuandoNaoHouverMecanismoDeAutenticacao() {
		assertThat(this.contexto.getBeanNamesForType(UserDetailsService.class)).isEmpty();
	}

	@Test
	void deveRegistrarUmaUnicaFabricaDeProblemasQuandoSegurancaConfigImportarOsTratadores() {
		assertThat(this.contexto.getBeanNamesForType(FabricaDeProblemas.class)).hasSize(1);
	}

}
