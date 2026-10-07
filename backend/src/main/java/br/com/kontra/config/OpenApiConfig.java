package br.com.kontra.config;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados fixos do contrato OpenAPI (ADR-004).
 *
 * <p>{@code info} e {@code servers} são fixos para o documento gerado ser determinístico: sem o
 * {@code servers} explícito, o springdoc o calcularia a partir da requisição (host e porta), e o snapshot
 * versionado ({@code openapi/openapi.json}) mudaria a cada execução. O servidor {@code /} faz o cliente
 * gerado usar URLs relativas (mesma origem, ADR-005).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	static final String TITULO = "API do Kontra";

	static final String VERSAO = "0.1.0";

	@Bean
	OpenAPI contratoDaApi() {
		return new OpenAPI().info(new Info().title(TITULO).version(VERSAO)).servers(List.of(new Server().url("/")));
	}

}
