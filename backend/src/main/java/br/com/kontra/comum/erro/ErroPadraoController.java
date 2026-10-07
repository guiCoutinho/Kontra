package br.com.kontra.comum.erro;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Substitui o {@code BasicErrorController} do Boot em {@code /error}: os erros que não passam pelo Spring MVC
 * (ex.: {@code sendError} do contêiner ou de filtros, exceções lançadas fora do {@code DispatcherServlet}) e o
 * acesso direto a {@code /error} também respondem em Problem Details, em português, sem a página
 * "Whitelabel" nem o JSON padrão do Boot.
 *
 * <ul>
 * <li>status: o do despacho de erro ({@link RequestDispatcher#ERROR_STATUS_CODE}); acesso direto → 404;
 * status fora de 4xx/5xx → 500;
 * <li>{@code codigo}: {@link CodigoErro#deStatus}; {@code detail}: o padrão do código (nunca a mensagem
 * interna); {@code instance}: o caminho original ({@link RequestDispatcher#ERROR_REQUEST_URI});
 * <li>sempre {@code application/problem+json}, qualquer que seja o {@code Accept}, com
 * {@code X-Content-Type-Options: nosniff} e {@code Cache-Control: no-store}.
 * </ul>
 */
@RestController
@RequestMapping("${spring.web.error.path:${error.path:/error}}")
public class ErroPadraoController implements ErrorController {

	private final FabricaDeProblemas fabricaDeProblemas;

	public ErroPadraoController(FabricaDeProblemas fabricaDeProblemas) {
		this.fabricaDeProblemas = fabricaDeProblemas;
	}

	@RequestMapping
	ResponseEntity<ProblemDetail> tratarErro(HttpServletRequest requisicao, HttpServletResponse resposta) {
		// No despacho de erro do contêiner o HeaderWriterFilter não atua: os cabeçalhos de segurança são
		// escritos aqui (no acesso direto, os do Spring Security não os duplicam).
		this.fabricaDeProblemas.aplicarCabecalhosDeSeguranca(resposta);
		HttpStatusCode status = statusDo(requisicao);
		ProblemDetail problema = this.fabricaDeProblemas.criar(status, CodigoErro.deStatus(status), null, requisicao);
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
	}

	private static HttpStatusCode statusDo(HttpServletRequest requisicao) {
		Object status = requisicao.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (status == null) {
			// Acesso direto a /error, fora de um despacho de erro.
			return HttpStatus.NOT_FOUND;
		}
		if (status instanceof Integer codigo && codigo >= 400 && codigo <= 599) {
			return HttpStatusCode.valueOf(codigo);
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

}
