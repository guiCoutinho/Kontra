package br.com.kontra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import br.com.kontra.suporte.TesteDeIntegracao;

/**
 * Mantém o snapshot versionado do contrato ({@code openapi/openapi.json}) igual ao OpenAPI gerado pelo
 * springdoc (ADR-004). O cliente TypeScript do frontend é gerado a partir desse arquivo.
 *
 * <ul>
 * <li>sem a propriedade: compara o gerado com o arquivo como árvore JSON (imune a CRLF/LF, indentação e ordem
 * das chaves) e falha, citando o comando de atualização, se forem diferentes;
 * <li>com {@code -Dopenapi.atualizarSnapshot=true}: regrava o arquivo (JSON indentado, LF, newline final) e
 * passa — exceto com a variável de ambiente {@code CI} definida, quando recusa e falha (no CI o snapshot só é
 * comparado);
 * <li>arquivo ausente ou com JSON inválido: falha citando o arquivo e o comando de atualização.
 * </ul>
 */
@TesteDeIntegracao
class SnapshotOpenApiIT {

	static final String PROPRIEDADE_ATUALIZAR_SNAPSHOT = "openapi.atualizarSnapshot";

	static final String COMANDO_DE_ATUALIZACAO = "./mvnw verify -D" + PROPRIEDADE_ATUALIZAR_SNAPSHOT + "=true";

	/** Definida pelos serviços de CI (ex.: o GitHub Actions define {@code CI=true}). */
	static final String VARIAVEL_DE_AMBIENTE_CI = "CI";

	/** {@code basedir} é definido pelo Surefire/Failsafe; numa IDE, o diretório de trabalho é o do módulo. */
	static final Path ARQUIVO_DO_SNAPSHOT = Path.of(System.getProperty("basedir", "."), "openapi",
			"openapi.json");

	private static final int MAXIMO_DE_DIFERENCAS_LISTADAS = 20;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void deveManterSnapshotIgualAoContratoGeradoQuandoApiMudar() throws Exception {
		String contratoGerado = obterContratoGerado();

		if (Boolean.getBoolean(PROPRIEDADE_ATUALIZAR_SNAPSHOT)) {
			if (System.getenv(VARIAVEL_DE_AMBIENTE_CI) != null) {
				fail("""
						Regravação do snapshot recusada: a variável de ambiente %s está definida.
						No CI o snapshot (%s) só é comparado, nunca regravado. Rode `%s` na sua máquina e commite o \
						arquivo.""", VARIAVEL_DE_AMBIENTE_CI, ARQUIVO_DO_SNAPSHOT.toAbsolutePath().normalize(),
						COMANDO_DE_ATUALIZACAO);
			}
			regravarSnapshot(contratoGerado);
			return;
		}

		JsonNode esperado = lerSnapshot(this.jsonMapper);
		JsonNode gerado = this.jsonMapper.readTree(contratoGerado);
		if (!esperado.equals(gerado)) {
			List<String> diferencas = new ArrayList<>();
			listarDiferencas("", esperado, gerado, diferencas);
			if (diferencas.size() >= MAXIMO_DE_DIFERENCAS_LISTADAS) {
				diferencas.add("... (lista truncada em " + MAXIMO_DE_DIFERENCAS_LISTADAS + ")");
			}
			fail("""
					O contrato OpenAPI gerado difere do snapshot versionado (%s).
					Se a mudança de contrato é intencional, rode `%s` e commite o openapi/openapi.json junto com o \
					código (e depois regenere o cliente do frontend).
					Diferenças (snapshot -> gerado):
					%s""", ARQUIVO_DO_SNAPSHOT.toAbsolutePath().normalize(), COMANDO_DE_ATUALIZACAO,
					String.join(System.lineSeparator(), diferencas));
		}
	}

	@Test
	void deveTerMetadadosFixosEApenasRotasDaApiQuandoGerarContrato() throws Exception {
		JsonNode contrato = this.jsonMapper.readTree(obterContratoGerado());

		assertThat(contrato.path("openapi").asString()).startsWith("3.1");
		assertThat(contrato.at("/info/title").asString()).isEqualTo("API do Kontra");
		assertThat(contrato.at("/info/version").asString()).isEqualTo("0.1.0");
		assertThat(contrato.path("servers").size()).isEqualTo(1);
		assertThat(contrato.at("/servers/0/url").asString()).isEqualTo("/");
		// Nem /error (ErroPadraoController) nem rotas de teste ou técnicas entram no contrato.
		assertThat(contrato.path("paths").propertyNames()).allSatisfy(caminho -> assertThat(caminho).startsWith("/api/"));
	}

	/**
	 * Lê o snapshot versionado. Arquivo ausente ou com JSON inválido falha com mensagem que cita o arquivo e o
	 * comando de atualização (em vez da exceção crua do Jackson). Usado também pelo {@link AderenciaAoContratoIT}.
	 */
	static JsonNode lerSnapshot(JsonMapper jsonMapper) throws Exception {
		if (!Files.exists(ARQUIVO_DO_SNAPSHOT)) {
			return fail("O snapshot do contrato OpenAPI não existe (%s). Rode `%s` e versione o arquivo.",
					ARQUIVO_DO_SNAPSHOT.toAbsolutePath().normalize(), COMANDO_DE_ATUALIZACAO);
		}
		String conteudo = Files.readString(ARQUIVO_DO_SNAPSHOT, StandardCharsets.UTF_8);
		try {
			return jsonMapper.readTree(conteudo);
		}
		catch (JacksonException ex) {
			return fail("""
					O snapshot do contrato OpenAPI (%s) não é um JSON válido: %s
					O arquivo é gerado, não editado à mão. Rode `%s` para regravá-lo a partir do código e commite o \
					resultado.""", ARQUIVO_DO_SNAPSHOT.toAbsolutePath().normalize(), ex.getOriginalMessage(),
					COMANDO_DE_ATUALIZACAO);
		}
	}

	private String obterContratoGerado() throws Exception {
		return this.mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
	}

	/** JSON do springdoc (já indentado e com chaves ordenadas) com LF e newline final, igual em Windows e Linux. */
	private static void regravarSnapshot(String contratoGerado) throws Exception {
		String conteudo = contratoGerado.replace("\r\n", "\n").strip() + "\n";
		Files.createDirectories(ARQUIVO_DO_SNAPSHOT.getParent());
		if (!Files.exists(ARQUIVO_DO_SNAPSHOT)
				|| !conteudo.equals(Files.readString(ARQUIVO_DO_SNAPSHOT, StandardCharsets.UTF_8))) {
			Files.writeString(ARQUIVO_DO_SNAPSHOT, conteudo, StandardCharsets.UTF_8);
		}
	}

	/** Lista as diferenças como JSON Pointer, para a mensagem de falha apontar onde o contrato mudou. */
	private static void listarDiferencas(String caminho, JsonNode esperado, JsonNode gerado, List<String> diferencas) {
		if (diferencas.size() >= MAXIMO_DE_DIFERENCAS_LISTADAS || esperado.equals(gerado)) {
			return;
		}
		String ponteiro = caminho.isEmpty() ? "/" : caminho;
		if (esperado.isObject() && gerado.isObject()) {
			Set<String> chaves = new TreeSet<>(esperado.propertyNames());
			chaves.addAll(gerado.propertyNames());
			for (String chave : chaves) {
				String filho = caminho + "/" + chave.replace("~", "~0").replace("/", "~1");
				if (!esperado.has(chave)) {
					diferencas.add("+ " + filho + " (adicionado)");
				}
				else if (!gerado.has(chave)) {
					diferencas.add("- " + filho + " (removido)");
				}
				else {
					listarDiferencas(filho, esperado.get(chave), gerado.get(chave), diferencas);
				}
				if (diferencas.size() >= MAXIMO_DE_DIFERENCAS_LISTADAS) {
					return;
				}
			}
		}
		else if (esperado.isArray() && gerado.isArray() && esperado.size() == gerado.size()) {
			for (int i = 0; i < esperado.size(); i++) {
				listarDiferencas(caminho + "/" + i, esperado.get(i), gerado.get(i), diferencas);
			}
		}
		else {
			diferencas.add("~ " + ponteiro + ": " + resumo(esperado) + " -> " + resumo(gerado));
		}
	}

	private static String resumo(JsonNode valor) {
		String texto = valor.toString();
		return (texto.length() > 120) ? texto.substring(0, 117) + "..." : texto;
	}

}
