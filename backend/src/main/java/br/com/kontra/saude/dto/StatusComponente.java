package br.com.kontra.saude.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Status de um componente na verificação de saúde. */
@Schema(enumAsRef = true, description = "Status de um componente verificado: DISPONIVEL ou INDISPONIVEL.")
public enum StatusComponente {

	DISPONIVEL,

	INDISPONIVEL

}
