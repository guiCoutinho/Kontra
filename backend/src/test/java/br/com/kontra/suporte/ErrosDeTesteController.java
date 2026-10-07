package br.com.kontra.suporte;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.kontra.comum.erro.CodigoErro;
import br.com.kontra.comum.erro.ExcecaoApi;

/**
 * Controller auxiliar, só de teste, que provoca cada cenário de erro.
 *
 * <p>{@link TestComponent} o exclui da varredura de componentes: ele só existe nos testes que o importam
 * explicitamente ({@code @Import(ErrosDeTesteController.class)}), nunca na aplicação nem nos {@code *IT}
 * (e, portanto, nunca no snapshot do OpenAPI).
 */
@TestComponent
@RestController
@RequestMapping(ErrosDeTesteController.CAMINHO_BASE)
public class ErrosDeTesteController {

	public static final String CAMINHO_BASE = "/api/teste-de-erros";

	public static final String MENSAGEM_INTERNA_SENSIVEL = "falha interna: senha=segredo123 em SELECT * FROM tabela_secreta";

	private final Validator validator;

	public ErrosDeTesteController(Validator validator) {
		this.validator = validator;
	}

	public record DadosDeTesteRequest(@NotBlank String nome, @NotNull @Positive Integer quantidade) {
	}

	/** Restrição de classe (erro "global", sem campo). */
	@PeriodoValido
	public record PeriodoDeTesteRequest(Integer inicio, Integer fim) {
	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = ValidadorDePeriodo.class)
	public @interface PeriodoValido {

		String message() default "o fim deve ser posterior ao início";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

	}

	public static class ValidadorDePeriodo implements ConstraintValidator<PeriodoValido, PeriodoDeTesteRequest> {

		@Override
		public boolean isValid(PeriodoDeTesteRequest periodo, ConstraintValidatorContext contexto) {
			return periodo == null || periodo.inicio() == null || periodo.fim() == null
					|| periodo.fim() > periodo.inicio();
		}

	}

	public record ItemDeTeste(@NotNull @Positive Integer quantidade) {
	}

	public record RequisicaoDeTeste(@NotBlank String nome, List<@Valid ItemDeTeste> itens) {
	}

	/** Restrição entre parâmetros (cross-parameter): o fim tem de ser maior que o início. */
	@Target({ ElementType.METHOD, ElementType.CONSTRUCTOR })
	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = ValidadorDeIntervalo.class)
	public @interface IntervaloValido {

		String message() default "o fim deve ser maior que o início";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

	}

	@SupportedValidationTarget(ValidationTarget.PARAMETERS)
	public static class ValidadorDeIntervalo implements ConstraintValidator<IntervaloValido, Object[]> {

		@Override
		public boolean isValid(Object[] valores, ConstraintValidatorContext contexto) {
			return (int) valores[1] > (int) valores[0];
		}

	}

	/** Alvo de validação de método, como um service com {@code @Validated}. */
	public static class ServicoDeTeste {

		public void criar(@Valid RequisicaoDeTeste requisicao, @Positive int valor) {
			// só a assinatura importa
		}

		@IntervaloValido
		public void reservar(int inicio, int fim) {
			// só a assinatura importa
		}

	}

	@GetMapping("/ok")
	Map<String, String> responderOk() {
		return Map.of("resultado", "ok");
	}

	@PostMapping("/corpo")
	DadosDeTesteRequest receberCorpo(@Valid @RequestBody DadosDeTesteRequest dados) {
		return dados;
	}

	@GetMapping("/parametro")
	String receberParametro(@RequestParam @NotBlank String valor) {
		return valor;
	}

	@GetMapping("/numero")
	int receberNumero(@RequestParam int valor) {
		return valor;
	}

	@PostMapping("/violacao-de-restricao")
	void violarRestricao(@RequestBody DadosDeTesteRequest dados) {
		Set<ConstraintViolation<DadosDeTesteRequest>> violacoes = this.validator.validate(dados);
		if (!violacoes.isEmpty()) {
			throw new ConstraintViolationException(violacoes);
		}
	}

	@PostMapping("/restricao-de-classe")
	PeriodoDeTesteRequest receberPeriodo(@Valid @RequestBody PeriodoDeTesteRequest periodo) {
		return periodo;
	}

	@PostMapping("/violacao-de-restricao-de-classe")
	void violarRestricaoDeClasse(@RequestBody PeriodoDeTesteRequest periodo) {
		Set<ConstraintViolation<PeriodoDeTesteRequest>> violacoes = this.validator.validate(periodo);
		if (!violacoes.isEmpty()) {
			throw new ConstraintViolationException(violacoes);
		}
	}

	/**
	 * Violações de validação de método (caminhos como {@code criar.requisicao.nome}), como as que um service
	 * com {@code @Validated} lança.
	 */
	@PostMapping("/validacao-de-metodo")
	void violarValidacaoDeMetodo() throws NoSuchMethodException {
		Method criar = ServicoDeTeste.class.getMethod("criar", RequisicaoDeTeste.class, int.class);
		Object[] argumentos = { new RequisicaoDeTeste(" ", List.of(new ItemDeTeste(0))), 0 };
		Set<ConstraintViolation<ServicoDeTeste>> violacoes = this.validator.forExecutables()
			.validateParameters(new ServicoDeTeste(), criar, argumentos);
		if (!violacoes.isEmpty()) {
			throw new ConstraintViolationException(violacoes);
		}
	}

	/** Violação de restrição entre parâmetros (caminho {@code reservar.<cross-parameter>}). */
	@PostMapping("/validacao-entre-parametros")
	void violarValidacaoEntreParametros() throws NoSuchMethodException {
		Method reservar = ServicoDeTeste.class.getMethod("reservar", int.class, int.class);
		Set<ConstraintViolation<ServicoDeTeste>> violacoes = this.validator.forExecutables()
			.validateParameters(new ServicoDeTeste(), reservar, new Object[] { 10, 5 });
		if (!violacoes.isEmpty()) {
			throw new ConstraintViolationException(violacoes);
		}
	}

	@GetMapping("/excecao-api")
	void lancarExcecaoApi() {
		throw new ExcecaoApi(HttpStatus.CONFLICT, CodigoErro.CONFLITO, "Já existe um registro com esse nome.");
	}

	@GetMapping("/excecao-api-com-status-padrao")
	void lancarExcecaoApiComStatusPadrao() {
		throw new ExcecaoApi(CodigoErro.NAO_ENCONTRADO, "O contrato 42 não foi encontrado.");
	}

	@GetMapping("/excecao-inesperada")
	void lancarExcecaoInesperada() {
		throw new IllegalStateException(MENSAGEM_INTERNA_SENSIVEL);
	}

	@GetMapping("/acesso-negado")
	void negarAcesso() {
		throw new AccessDeniedException("acesso negado pelo teste");
	}

}
