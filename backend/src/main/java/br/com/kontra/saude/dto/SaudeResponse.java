package br.com.kontra.saude.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Resultado da verificação de saúde ({@code GET /api/saude}).
 *
 * @param status status agregado: {@link StatusComponente#DISPONIVEL} se e somente se todos os componentes
 * estão disponíveis
 * @param bancoDeDados status da conexão com o PostgreSQL
 * @param verificadoEm momento da verificação (UTC)
 */
@Schema(description = "Resultado da verificação de saúde da API.")
public record SaudeResponse(

		@Schema(description = "Status agregado: DISPONIVEL se e somente se todos os componentes estão DISPONIVEL.",
				requiredMode = RequiredMode.REQUIRED)
		StatusComponente status,

		@Schema(description = "Status da conexão com o banco de dados (PostgreSQL).", requiredMode = RequiredMode.REQUIRED)
		StatusComponente bancoDeDados,

		@Schema(description = "Momento da verificação (UTC, precisão de milissegundos).",
				requiredMode = RequiredMode.REQUIRED)
		Instant verificadoEm) {
}
