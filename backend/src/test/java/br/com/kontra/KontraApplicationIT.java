package br.com.kontra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.kontra.suporte.TestcontainersConfig;

@SpringBootTest
@Import(TestcontainersConfig.class)
class KontraApplicationIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void deveAplicarMigracaoInicialAoSubir() {
		List<Boolean> sucessosDaVersao1 = jdbcTemplate.queryForList(
				"select success from flyway_schema_history where version = ?", Boolean.class, "1");

		assertThat(sucessosDaVersao1).containsExactly(true);
	}

}
