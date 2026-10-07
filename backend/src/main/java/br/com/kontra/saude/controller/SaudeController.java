package br.com.kontra.saude.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.kontra.comum.erro.ProblemaResponse;
import br.com.kontra.saude.dto.SaudeResponse;
import br.com.kontra.saude.dto.StatusComponente;
import br.com.kontra.saude.service.SaudeService;

/** Verificação de saúde (ADR-008): pública, consumida pela tela inicial e pelo healthcheck do contêiner. */
@RestController
@RequestMapping("/api/saude")
@Tag(name = "saude", description = "Verificação de saúde da API e de suas dependências.")
public class SaudeController {

	private final SaudeService saudeService;

	public SaudeController(SaudeService saudeService) {
		this.saudeService = saudeService;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "obterSaude", summary = "Verifica a saúde da API",
			description = "Verifica a API e a conexão com o banco de dados. Responde 200 quando tudo está disponível "
					+ "e 503 quando algum componente está indisponível. Não exige autenticação.")
	@ApiResponse(responseCode = "200", description = "API e banco de dados disponíveis.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = SaudeResponse.class)))
	@ApiResponse(responseCode = "503", description = "Algum componente (ex.: banco de dados) está indisponível.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = SaudeResponse.class)))
	@ApiResponse(responseCode = "500", description = "Erro inesperado.",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemaResponse.class)))
	public ResponseEntity<SaudeResponse> obterSaude() {
		SaudeResponse saude = this.saudeService.verificarBancoDeDados();
		HttpStatus status = (saude.status() == StatusComponente.DISPONIVEL) ? HttpStatus.OK
				: HttpStatus.SERVICE_UNAVAILABLE;
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(saude);
	}

}
