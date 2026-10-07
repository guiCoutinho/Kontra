package br.com.kontra.suporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 18 real para os testes de integração ({@code *IT}).
 *
 * <p>Importe com {@code @Import(TestcontainersConfig.class)} junto de {@code @SpringBootTest}: o
 * {@code @ServiceConnection} liga o datasource ao container, e o cache de contexto do Spring faz
 * todos os {@code *IT} com a mesma configuração compartilharem um único container por execução.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

	/** Mesma imagem do serviço {@code banco} do docker compose. */
	static final DockerImageName IMAGEM_POSTGRES = DockerImageName.parse("postgres:18-alpine");

	@Bean
	@ServiceConnection
	PostgreSQLContainer containerPostgres() {
		return new PostgreSQLContainer(IMAGEM_POSTGRES);
	}

}
