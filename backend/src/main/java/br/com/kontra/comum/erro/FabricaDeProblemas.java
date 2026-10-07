package br.com.kontra.comum.erro;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Único ponto de criação das respostas de erro da API (RFC 9457, ver {@link ProblemaResponse}).
 *
 * <p>Usada pelo {@link TratadorGlobalDeExcecoes} (erros do Spring MVC), pelo {@link ErroPadraoController}
 * ({@code /error}: erros do contêiner e de filtros) e pelos tratadores do Spring Security
 * ({@link TratadorNaoAutenticado}, {@link TratadorAcessoNegado}, {@link TratadorRequisicaoRejeitada}), que
 * rodam fora do MVC e escrevem a resposta com {@link #escreverNaResposta}.
 */
@Component
public class FabricaDeProblemas {

	static final URI TIPO_PADRAO = URI.create("about:blank");

	static final String PROPRIEDADE_CODIGO = "codigo";

	static final String PROPRIEDADE_OCORRIDO_EM = "ocorridoEm";

	static final String PROPRIEDADE_ERROS = "erros";

	static final String CABECALHO_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

	private static final Comparator<ErroDeCampo> ORDEM_DOS_ERROS = Comparator.comparing(ErroDeCampo::campo)
		.thenComparing(ErroDeCampo::mensagem);

	private final JsonMapper jsonMapper;

	private final Clock relogio;

	@Autowired
	public FabricaDeProblemas(JsonMapper jsonMapper) {
		this(jsonMapper, Clock.systemUTC());
	}

	FabricaDeProblemas(JsonMapper jsonMapper, Clock relogio) {
		this.jsonMapper = jsonMapper;
		this.relogio = relogio;
	}

	/** Problema com o status e o {@code detail} padrões do código. */
	public ProblemDetail criar(CodigoErro codigo, HttpServletRequest requisicao) {
		return criar(codigo.status(), codigo, null, requisicao);
	}

	/**
	 * Problema com status e {@code detail} explícitos.
	 * @param detalhe mensagem em pt-BR segura para o usuário; {@code null} usa o detalhe padrão do código
	 */
	public ProblemDetail criar(HttpStatusCode status, CodigoErro codigo, @Nullable String detalhe,
			HttpServletRequest requisicao) {
		ProblemDetail problema = ProblemDetail.forStatus(status);
		// No Spring 7 o "type" não tem mais valor padrão; o contrato exige sempre about:blank.
		problema.setType(TIPO_PADRAO);
		problema.setTitle(codigo.titulo());
		problema.setDetail((detalhe != null) ? detalhe : codigo.detalhePadrao());
		problema.setInstance(caminhoDa(requisicao));
		problema.setProperty(PROPRIEDADE_CODIGO, codigo);
		problema.setProperty(PROPRIEDADE_OCORRIDO_EM, Instant.now(this.relogio).truncatedTo(ChronoUnit.MILLIS));
		return problema;
	}

	/** Problema {@link CodigoErro#ERRO_VALIDACAO} (400) com os erros por campo, ordenados por campo. */
	public ProblemDetail criarErroDeValidacao(List<ErroDeCampo> erros, HttpServletRequest requisicao) {
		ProblemDetail problema = criar(CodigoErro.ERRO_VALIDACAO, requisicao);
		problema.setProperty(PROPRIEDADE_ERROS, erros.stream().sorted(ORDEM_DOS_ERROS).toList());
		return problema;
	}

	/**
	 * Cabeçalhos de segurança mínimos ({@code X-Content-Type-Options: nosniff}, {@code Cache-Control: no-store})
	 * para respostas de erro escritas onde o {@code HeaderWriterFilter} do Spring Security não atua: rejeição
	 * do firewall e despacho de erro do contêiner. Onde ele atua, não há duplicação: os escritores do Spring
	 * Security não sobrescrevem cabeçalhos já presentes.
	 */
	public void aplicarCabecalhosDeSeguranca(HttpServletResponse resposta) {
		resposta.setHeader(CABECALHO_X_CONTENT_TYPE_OPTIONS, "nosniff");
		resposta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
	}

	/**
	 * Escreve o problema diretamente na resposta, como {@code application/problem+json}. Para quem roda
	 * fora do Spring MVC (filtros do Spring Security); usa o mesmo {@link JsonMapper} do MVC.
	 */
	public void escreverNaResposta(ProblemDetail problema, HttpServletResponse resposta) throws IOException {
		if (resposta.isCommitted()) {
			return;
		}
		resposta.setStatus(problema.getStatus());
		resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		this.jsonMapper.writeValue(resposta.getOutputStream(), problema);
	}

	/**
	 * Caminho da requisição original: num despacho de erro do contêiner (ex.: {@code /error}), o
	 * {@code getRequestURI()} é o da página de erro, e o caminho original fica em
	 * {@link RequestDispatcher#ERROR_REQUEST_URI}.
	 */
	private static @Nullable URI caminhoDa(HttpServletRequest requisicao) {
		Object caminhoOriginal = requisicao.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		String caminho = (caminhoOriginal instanceof String texto) ? texto : requisicao.getRequestURI();
		try {
			return URI.create(caminho);
		}
		catch (IllegalArgumentException ex) {
			// Caminho que não forma uma URI válida: o campo "instance" é opcional.
			return null;
		}
	}

}
