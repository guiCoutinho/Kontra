package br.com.kontra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;

/**
 * Garante que as bibliotecas usadas em tempo de execução estão nas versões com as correções de CVEs exigidas pela
 * auditoria de segurança do Marco 0. Hoje elas vêm da sobrescrita de {@code tomcat.version},
 * {@code jackson-bom.version} e {@code jackson-2-bom.version} no {@code pom.xml}, porque o Spring Boot 4.1.1 gerencia
 * versões vulneráveis. Se alguém remover a sobrescrita antes de o Boot gerenciar versões iguais ou maiores, este
 * teste falha.
 *
 * <p>Confere as versões carregadas no classpath (não a configuração do Maven), comparando numericamente cada
 * componente da versão.
 */
class VersoesMinimasDeSegurancaTest {

	@Test
	void deveUsarTomcatComCorrecoesDeSegurancaQuandoEmbutidoNaAplicacao() {
		// Ex.: "11.0.26.0" (maior.menor.patch.build).
		verificarVersaoMinima("Tomcat (tomcat-embed-core)", ServerInfo.getServerNumber(), "11.0.26");
	}

	@Test
	void deveUsarJackson3ComCorrecoesDeSegurancaQuandoSerializarJsonDaAplicacao() {
		tools.jackson.core.Version versao = tools.jackson.databind.cfg.PackageVersion.VERSION;
		verificarVersaoMinima("Jackson 3 (tools.jackson.core:jackson-databind)",
				versao.getMajorVersion() + "." + versao.getMinorVersion() + "." + versao.getPatchLevel(), "3.1.7");
	}

	@Test
	void deveUsarJackson2ComCorrecoesDeSegurancaQuandoSpringdocGerarContrato() {
		com.fasterxml.jackson.core.Version versao = com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION;
		verificarVersaoMinima("Jackson 2 (com.fasterxml.jackson.core:jackson-databind)",
				versao.getMajorVersion() + "." + versao.getMinorVersion() + "." + versao.getPatchLevel(), "2.21.7");
	}

	@Test
	void deveCompararVersoesNumericamenteQuandoComponentesTiveremTamanhosDiferentes() {
		assertThat(compararVersoes("11.0.9", "11.0.26")).isNegative();
		assertThat(compararVersoes("11.0.26.0", "11.0.26")).isZero();
		assertThat(compararVersoes("11.0.27", "11.0.26")).isPositive();
		assertThat(compararVersoes("3.2.0", "3.1.7")).isPositive();
		assertThat(compararVersoes("2.21.7-SNAPSHOT", "2.21.7")).isZero();
		assertThat(compararVersoes("2.21.10", "2.21.7")).isPositive();
	}

	private static void verificarVersaoMinima(String componente, String versaoEmUso, String versaoMinima) {
		assertThat(compararVersoes(versaoEmUso, versaoMinima))
			.withFailMessage("%s %s: versão abaixo do mínimo de segurança (auditoria do Marco 0) — não remova a "
					+ "sobrescrita no pom.xml antes de o Spring Boot gerenciar >= %s.", componente, versaoEmUso,
					versaoMinima)
			.isGreaterThanOrEqualTo(0);
	}

	/**
	 * Compara versões numéricas componente a componente ({@code 11.0.9 < 11.0.26}); componentes ausentes valem 0 e
	 * sufixos não numéricos (ex.: {@code -SNAPSHOT}) são ignorados.
	 */
	static int compararVersoes(String versao, String outra) {
		int[] a = componentesNumericos(versao);
		int[] b = componentesNumericos(outra);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int diferenca = Integer.compare((i < a.length) ? a[i] : 0, (i < b.length) ? b[i] : 0);
			if (diferenca != 0) {
				return diferenca;
			}
		}
		return 0;
	}

	private static int[] componentesNumericos(String versao) {
		return Arrays.stream(versao.split("[.-]"))
			.takeWhile(parte -> parte.matches("\\d+"))
			.mapToInt(Integer::parseInt)
			.toArray();
	}

}
