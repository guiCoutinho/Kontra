package br.com.kontra.saude.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import br.com.kontra.config.SegurancaConfig;
import br.com.kontra.saude.dto.SaudeResponse;
import br.com.kontra.saude.dto.StatusComponente;
import br.com.kontra.saude.service.SaudeService;

/** {@code GET /api/saude} com a {@link SegurancaConfig} real e o service simulado; sem autenticação. */
@WebMvcTest(SaudeController.class)
@Import(SegurancaConfig.class)
class SaudeControllerTest {

	private static final Instant VERIFICADO_EM = Instant.parse("2026-10-06T21:00:00.123Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SaudeService saudeService;

	@Test
	void deveResponderOkComTudoDisponivelSemAutenticacaoQuandoBancoEstiverDisponivel() throws Exception {
		given(this.saudeService.verificarBancoDeDados()).willReturn(
				new SaudeResponse(StatusComponente.DISPONIVEL, StatusComponente.DISPONIVEL, VERIFICADO_EM));

		MvcResult resultado = this.mockMvc.perform(get("/api/saude"))
			.andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON),
					header().string(HttpHeaders.CACHE_CONTROL, "no-store"), jsonPath("$.status").value("DISPONIVEL"),
					jsonPath("$.bancoDeDados").value("DISPONIVEL"),
					jsonPath("$.verificadoEm").value("2026-10-06T21:00:00.123Z"),
					header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andReturn();

		assertThat(resultado.getRequest().getSession(false)).as("sessão HTTP criada").isNull();
	}

	@Test
	void deveResponderServicoIndisponivelComTudoIndisponivelQuandoBancoEstiverIndisponivel() throws Exception {
		given(this.saudeService.verificarBancoDeDados()).willReturn(
				new SaudeResponse(StatusComponente.INDISPONIVEL, StatusComponente.INDISPONIVEL, VERIFICADO_EM));

		this.mockMvc.perform(get("/api/saude"))
			.andExpectAll(status().isServiceUnavailable(), content().contentType(MediaType.APPLICATION_JSON),
					header().string(HttpHeaders.CACHE_CONTROL, "no-store"), jsonPath("$.status").value("INDISPONIVEL"),
					jsonPath("$.bancoDeDados").value("INDISPONIVEL"),
					jsonPath("$.verificadoEm").value("2026-10-06T21:00:00.123Z"));
	}

	@Test
	void deveResponderErroInternoSemDetalhesQuandoVerificacaoFalharInesperadamente() throws Exception {
		given(this.saudeService.verificarBancoDeDados())
			.willThrow(new IllegalStateException("falha interna: pool encerrado"));

		this.mockMvc.perform(get("/api/saude"))
			.andExpectAll(status().isInternalServerError(), content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
					jsonPath("$.codigo").value("ERRO_INTERNO"), jsonPath("$.title").value("Erro interno"),
					jsonPath("$.instance").value("/api/saude"),
					content().string(not(containsString("IllegalStateException"))),
					content().string(not(containsString("pool encerrado"))));
	}

	@Test
	void deveExigirAutenticacaoQuandoMetodoNaoForGet() throws Exception {
		this.mockMvc.perform(head("/api/saude")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/saude"))
			.andExpectAll(status().isUnauthorized(), jsonPath("$.codigo").value("NAO_AUTENTICADO"));
	}

	@Test
	void deveResponderErroDeRequisicaoQuandoClienteNaoAceitarJson() throws Exception {
		this.mockMvc.perform(get("/api/saude").accept(MediaType.APPLICATION_XML))
			.andExpectAll(status().isNotAcceptable(), content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
					jsonPath("$.codigo").value("ERRO_REQUISICAO"),
					jsonPath("$.detail").value("O formato de resposta solicitado não está disponível para este recurso."));
	}

}
