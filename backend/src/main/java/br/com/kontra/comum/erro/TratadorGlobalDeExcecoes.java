package br.com.kontra.comum.erro;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte toda exceção que chega ao Spring MVC em Problem Details (RFC 9457) pela
 * {@link FabricaDeProblemas}.
 *
 * <ul>
 * <li>exceções do próprio Spring MVC (herdadas de {@link ResponseEntityExceptionHandler}): o corpo padrão
 * (em inglês) é substituído pelo formato do projeto, mantendo status e cabeçalhos (ex.: {@code Allow});
 * <li>{@link ExcecaoApi}: status, código e mensagem da exceção;
 * <li>{@link ConstraintViolationException}: 400 {@link CodigoErro#ERRO_VALIDACAO};
 * <li>qualquer outra exceção: 500 {@link CodigoErro#ERRO_INTERNO}, com detalhe genérico (stack trace só no log).
 * </ul>
 *
 * <p>Os métodos devolvem {@link ResponseEntity} (sem {@code @ResponseStatus}) para o springdoc não injetar
 * respostas genéricas em todas as operações. O {@code Content-Type} é sempre fixado em
 * {@code application/problem+json}, mesmo quando o cliente pede {@code Accept: application/json}.
 */
@RestControllerAdvice
public class TratadorGlobalDeExcecoes extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(TratadorGlobalDeExcecoes.class);

	/**
	 * Valor de {@code campo} para erros que não pertencem a um campo (restrições de classe): neutro, para não
	 * expor o nome derivado da classe Java.
	 */
	static final String CAMPO_DA_REQUISICAO = "requisicao";

	/** Valor de {@code campo} para restrições que envolvem vários parâmetros do método. */
	static final String CAMPO_DOS_PARAMETROS = "parametros";

	private final FabricaDeProblemas fabricaDeProblemas;

	public TratadorGlobalDeExcecoes(FabricaDeProblemas fabricaDeProblemas) {
		this.fabricaDeProblemas = fabricaDeProblemas;
	}

	@ExceptionHandler(ExcecaoApi.class)
	ResponseEntity<Object> tratarExcecaoApi(ExcecaoApi excecao, HttpServletRequest requisicao) {
		if (excecao.status().is5xxServerError()) {
			log.error("Erro {} ao processar {} {}", excecao.codigo(), requisicao.getMethod(),
					requisicao.getRequestURI(), excecao);
		}
		ProblemDetail problema = this.fabricaDeProblemas.criar(excecao.status(), excecao.codigo(),
				excecao.getMessage(), requisicao);
		return respostaDeProblema(problema, new HttpHeaders());
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<Object> tratarViolacaoDeRestricao(ConstraintViolationException excecao,
			HttpServletRequest requisicao) {
		List<ErroDeCampo> erros = excecao.getConstraintViolations()
			.stream()
			.map(violacao -> new ErroDeCampo(campoDa(violacao), violacao.getMessage()))
			.toList();
		return respostaDeProblema(this.fabricaDeProblemas.criarErroDeValidacao(erros, requisicao), new HttpHeaders());
	}

	/**
	 * Exceções do Spring Security lançadas dentro de um controller seguem para o
	 * {@code ExceptionTranslationFilter}, que responde 401/403 pelos tratadores do Security; sem isso, o
	 * tratamento genérico abaixo as transformaria em 500.
	 */
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	void repassarAoSpringSecurity(RuntimeException excecao) {
		throw excecao;
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> tratarExcecaoInesperada(Exception excecao, HttpServletRequest requisicao) {
		log.error("Erro inesperado ao processar {} {}", requisicao.getMethod(), requisicao.getRequestURI(), excecao);
		return respostaDeProblema(this.fabricaDeProblemas.criar(CodigoErro.ERRO_INTERNO, requisicao),
				new HttpHeaders());
	}

	/** Ponto comum de todas as exceções do Spring MVC tratadas pela classe base. */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception excecao, @Nullable Object corpo,
			HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicaoWeb) {
		if (!(requisicaoWeb instanceof ServletWebRequest requisicaoServlet)) {
			return super.handleExceptionInternal(excecao, corpo, cabecalhos, status, requisicaoWeb);
		}
		HttpServletRequest requisicao = requisicaoServlet.getRequest();
		ProblemDetail problema = switch (excecao) {
			case MethodArgumentNotValidException ex ->
				this.fabricaDeProblemas.criarErroDeValidacao(errosDe(ex.getBindingResult()), requisicao);
			case HandlerMethodValidationException ex ->
				this.fabricaDeProblemas.criarErroDeValidacao(errosDe(ex), requisicao);
			default -> this.fabricaDeProblemas.criar(status, CodigoErro.deStatus(status), detalheDe(excecao),
					requisicao);
		};
		if (status.is5xxServerError()) {
			log.error("Erro {} ao processar {} {}", status.value(), requisicao.getMethod(), requisicao.getRequestURI(),
					excecao);
		}
		HttpHeaders cabecalhosDoProblema = HttpHeaders.copyOf(cabecalhos);
		cabecalhosDoProblema.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		return super.handleExceptionInternal(excecao, problema, cabecalhosDoProblema, status, requisicaoWeb);
	}

	private static ResponseEntity<Object> respostaDeProblema(ProblemDetail problema, HttpHeaders cabecalhos) {
		return ResponseEntity.status(problema.getStatus())
			.headers(cabecalhos)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(problema);
	}

	/** Detalhe em pt-BR para alguns casos comuns; {@code null} usa o detalhe padrão do código. */
	private static @Nullable String detalheDe(Exception excecao) {
		return switch (excecao) {
			case HttpRequestMethodNotSupportedException ex ->
				"O método " + ex.getMethod() + " não é permitido para este recurso.";
			case MissingServletRequestParameterException ex ->
				"O parâmetro obrigatório '" + ex.getParameterName() + "' não foi informado.";
			case TypeMismatchException ex when ex.getPropertyName() != null ->
				"O parâmetro '" + ex.getPropertyName() + "' tem um valor inválido.";
			case HttpMediaTypeNotAcceptableException ex ->
				"O formato de resposta solicitado não está disponível para este recurso.";
			case MaxUploadSizeExceededException ex -> "O conteúdo enviado excede o tamanho máximo permitido.";
			default -> null;
		};
	}

	private static List<ErroDeCampo> errosDe(Errors errosDeValidacao) {
		List<ErroDeCampo> erros = new ArrayList<>();
		errosDeValidacao.getFieldErrors()
			.forEach(erro -> erros.add(new ErroDeCampo(erro.getField(), mensagemDe(erro))));
		errosDeValidacao.getGlobalErrors()
			.forEach(erro -> erros.add(new ErroDeCampo(CAMPO_DA_REQUISICAO, mensagemDe(erro))));
		return erros;
	}

	private static List<ErroDeCampo> errosDe(HandlerMethodValidationException excecao) {
		List<ErroDeCampo> erros = new ArrayList<>();
		for (ParameterValidationResult resultado : excecao.getParameterValidationResults()) {
			if (resultado instanceof ParameterErrors errosDoParametro) {
				erros.addAll(errosDe(errosDoParametro));
			}
			else {
				String nome = nomeDoParametro(resultado.getMethodParameter());
				resultado.getResolvableErrors().forEach(erro -> erros.add(new ErroDeCampo(nome, mensagemDe(erro))));
			}
		}
		excecao.getCrossParameterValidationResults()
			.forEach(erro -> erros.add(new ErroDeCampo(CAMPO_DOS_PARAMETROS, mensagemDe(erro))));
		return erros;
	}

	private static String nomeDoParametro(MethodParameter parametro) {
		RequestParam requestParam = parametro.getParameterAnnotation(RequestParam.class);
		if (requestParam != null && StringUtils.hasText(requestParam.name())) {
			return requestParam.name();
		}
		PathVariable pathVariable = parametro.getParameterAnnotation(PathVariable.class);
		if (pathVariable != null && StringUtils.hasText(pathVariable.name())) {
			return pathVariable.name();
		}
		String nome = parametro.getParameterName();
		return (nome != null) ? nome : "parametro" + parametro.getParameterIndex();
	}

	/**
	 * Caminho do campo a partir do caminho da violação, sem o método e o parâmetro quando a validação é de
	 * método (ex.: {@code criar.requisicao.nome} → {@code nome}, {@code criar.valor} → {@code valor},
	 * {@code criar.requisicao.itens[0].quantidade} → {@code itens[0].quantidade}). Restrição de classe (caminho
	 * vazio) → {@value #CAMPO_DA_REQUISICAO}; restrição entre parâmetros → {@value #CAMPO_DOS_PARAMETROS}.
	 */
	private static String campoDa(ConstraintViolation<?> violacao) {
		List<Path.Node> nos = new ArrayList<>();
		violacao.getPropertyPath().forEach(nos::add);
		if (nos.isEmpty()) {
			return CAMPO_DA_REQUISICAO;
		}
		if (nos.getLast().getKind() == ElementKind.CROSS_PARAMETER) {
			return CAMPO_DOS_PARAMETROS;
		}
		int nosIgnorados = 0;
		if (nos.getFirst().getKind() == ElementKind.METHOD || nos.getFirst().getKind() == ElementKind.CONSTRUCTOR) {
			nosIgnorados = (nos.size() > 2) ? 2 : 1;
		}
		String[] partes = violacao.getPropertyPath().toString().split("\\.", nosIgnorados + 1);
		String campo = partes[partes.length - 1];
		return campo.isBlank() ? CAMPO_DA_REQUISICAO : campo;
	}

	private static String mensagemDe(MessageSourceResolvable erro) {
		String mensagem = erro.getDefaultMessage();
		return (mensagem != null) ? mensagem : CodigoErro.ERRO_VALIDACAO.detalhePadrao();
	}

}
