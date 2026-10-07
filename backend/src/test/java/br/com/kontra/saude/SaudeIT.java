package br.com.kontra.saude;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import br.com.kontra.suporte.TesteDeIntegracao;

/** {@code GET /api/saude} ponta a ponta: Tomcat real, Spring Security e PostgreSQL 18 (Testcontainers). */
@TesteDeIntegracao
class SaudeIT {

	private final HttpClient clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@LocalServerPort
	private int porta;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void deveResponderOkComTudoDisponivelQuandoBancoEstiverNoAr() throws Exception {
		Instant antes = Instant.now().minusSeconds(1);

		HttpResponse<String> resposta = this.clienteHttp.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + this.porta + "/api/saude")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(resposta.headers().firstValue(HttpHeaders.CONTENT_TYPE)).hasValueSatisfying(
				tipo -> assertThat(MediaType.parseMediaType(tipo).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
		assertThat(resposta.headers().allValues(HttpHeaders.CACHE_CONTROL)).containsExactly("no-store");
		assertThat(resposta.headers().allValues(HttpHeaders.SET_COOKIE)).isEmpty();

		JsonNode corpo = this.jsonMapper.readTree(resposta.body());
		assertThat(corpo.propertyNames()).containsExactlyInAnyOrder("status", "bancoDeDados", "verificadoEm");
		assertThat(corpo.get("status").asString()).isEqualTo("DISPONIVEL");
		assertThat(corpo.get("bancoDeDados").asString()).isEqualTo("DISPONIVEL");
		assertThat(Instant.parse(corpo.get("verificadoEm").asString())).isAfter(antes)
			.isBeforeOrEqualTo(Instant.now());
	}

}
