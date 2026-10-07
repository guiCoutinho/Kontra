package br.com.kontra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import br.com.kontra.comum.erro.ErroDeCampo;
import br.com.kontra.comum.erro.FabricaDeProblemas;
import br.com.kontra.suporte.TesteDeIntegracao;

/**
 * Confere que o JSON real da aplicação (Jackson 3) tem exatamente os campos que o contrato declara —
 * {@code properties} e {@code required} de cada schema — em duas fontes:
 *
 * <ul>
 * <li>o contrato gerado agora pelo springdoc ({@code /v3/api-docs}): fecha a lacuna entre o Jackson 2 do
 * swagger-core, que gera os schemas, e o Jackson 3 da aplicação, que serializa — uma anotação ou configuração
 * que só o Jackson 3 enxerga ({@code tools.jackson.databind.annotation.*}, {@code spring.jackson.*}) mudaria o
 * JSON sem mudar o contrato;
 * <li>o snapshot versionado ({@code openapi/openapi.json}), que é o que o frontend usa: falha também se um
 * campo for renomeado (ex.: {@code @JsonProperty}) sem o snapshot ser regravado. No modo de regravação
 * ({@code -Dopenapi.atualizarSnapshot=true}) esta fonte é ignorada, pois o arquivo está sendo refeito.
 * </ul>
 */
@TesteDeIntegracao
class AderenciaAoContratoIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private FabricaDeProblemas fabricaDeProblemas;

	/** Nome da fonte → {@code components.schemas} dela. */
	private final Map<String, JsonNode> contratos = new LinkedHashMap<>();

	@BeforeEach
	void lerContratos() throws Exception {
		String gerado = this.mockMvc.perform(get("/v3/api-docs"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		this.contratos.put("contrato gerado (/v3/api-docs)", this.jsonMapper.readTree(gerado).at("/components/schemas"));
		if (!Boolean.getBoolean(SnapshotOpenApiIT.PROPRIEDADE_ATUALIZAR_SNAPSHOT)) {
			this.contratos.put("snapshot " + SnapshotOpenApiIT.ARQUIVO_DO_SNAPSHOT.toAbsolutePath().normalize(),
					SnapshotOpenApiIT.lerSnapshot(this.jsonMapper).at("/components/schemas"));
		}
	}

	@Test
	void deveSerializarSaudeResponseComExatamenteOsCamposDoContratoQuandoResponderSaude() throws Exception {
		JsonNode corpo = corpoDe(get("/api/saude"));

		verificarCamposExatos("SaudeResponse", corpo);
		verificarValorDeEnum("StatusComponente", corpo.get("status"));
		verificarValorDeEnum("StatusComponente", corpo.get("bancoDeDados"));
	}

	@Test
	void deveSerializarProblemaResponseComExatamenteOsCamposDoContratoQuandoErroTiverTodosOsCampos() {
		// Mesmo JsonMapper (e mixin do ProblemDetail) que o Spring MVC usa para escrever as respostas de erro.
		JsonNode corpo = this.jsonMapper.valueToTree(this.fabricaDeProblemas.criarErroDeValidacao(
				List.of(new ErroDeCampo("nome", "não deve estar em branco")),
				new MockHttpServletRequest("POST", "/api/exemplo")));

		verificarCamposExatos("ProblemaResponse", corpo);
		verificarValorDeEnum("CodigoErro", corpo.get("codigo"));
		for (JsonNode erro : corpo.get("erros")) {
			verificarCamposExatos("ErroDeCampo", erro);
		}
	}

	@Test
	void deveConterCamposObrigatoriosDoContratoQuandoProblemaRealNaoTiverCamposOpcionais() throws Exception {
		JsonNode corpo = corpoDe(get("/api/inexistente"));

		assertThat(corpo.has("erros")).isFalse();
		verificarCamposPermitidosEObrigatorios("ProblemaResponse", corpo);
		verificarValorDeEnum("CodigoErro", corpo.get("codigo"));
	}

	private JsonNode corpoDe(RequestBuilder requisicao) throws Exception {
		return this.jsonMapper
			.readTree(this.mockMvc.perform(requisicao).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	/** O JSON tem exatamente as {@code properties} do schema (nem a mais, nem a menos) e todos os {@code required}. */
	private void verificarCamposExatos(String nomeDoSchema, JsonNode json) {
		this.contratos.forEach((fonte, schemas) -> assertThat(new TreeSet<>(json.propertyNames()))
			.as("campos do JSON real de %s × properties do schema no %s", nomeDoSchema, fonte)
			.isEqualTo(propriedades(schemas, nomeDoSchema, fonte)));
		verificarCamposPermitidosEObrigatorios(nomeDoSchema, json);
	}

	/** Todo campo do JSON está nas {@code properties}, e todo campo de {@code required} está no JSON. */
	private void verificarCamposPermitidosEObrigatorios(String nomeDoSchema, JsonNode json) {
		this.contratos.forEach((fonte, schemas) -> {
			assertThat(propriedades(schemas, nomeDoSchema, fonte))
				.as("properties do schema %s no %s × campos do JSON real", nomeDoSchema, fonte)
				.containsAll(json.propertyNames());
			Set<String> obrigatorios = new TreeSet<>();
			schema(schemas, nomeDoSchema, fonte).path("required").forEach(item -> obrigatorios.add(item.asString()));
			assertThat(json.propertyNames()).as("campos required do schema %s no %s × JSON real", nomeDoSchema, fonte)
				.containsAll(obrigatorios);
		});
	}

	private void verificarValorDeEnum(String nomeDoSchema, JsonNode valor) {
		this.contratos.forEach((fonte, schemas) -> {
			List<String> publicados = new ArrayList<>();
			schema(schemas, nomeDoSchema, fonte).path("enum").forEach(item -> publicados.add(item.asString()));
			assertThat(publicados).as("valores do enum %s no %s", nomeDoSchema, fonte).contains(valor.asString());
		});
	}

	private static Set<String> propriedades(JsonNode schemas, String nomeDoSchema, String fonte) {
		JsonNode propriedades = schema(schemas, nomeDoSchema, fonte).path("properties");
		assertThat(propriedades.isObject()).as("properties do schema %s no %s", nomeDoSchema, fonte).isTrue();
		return new TreeSet<>(propriedades.propertyNames());
	}

	private static JsonNode schema(JsonNode schemas, String nomeDoSchema, String fonte) {
		JsonNode schema = schemas.path(nomeDoSchema);
		assertThat(schema.isObject()).as("schema %s no %s", nomeDoSchema, fonte).isTrue();
		return schema;
	}

}
