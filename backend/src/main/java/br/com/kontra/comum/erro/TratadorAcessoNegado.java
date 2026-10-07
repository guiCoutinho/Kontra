package br.com.kontra.comum.erro;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Responde 403 {@link CodigoErro#ACESSO_NEGADO} (Problem Details) quando um usuário autenticado não tem
 * permissão. Roda no filtro do Spring Security, fora do Spring MVC.
 */
@Component
public class TratadorAcessoNegado implements AccessDeniedHandler {

	private final FabricaDeProblemas fabricaDeProblemas;

	public TratadorAcessoNegado(FabricaDeProblemas fabricaDeProblemas) {
		this.fabricaDeProblemas = fabricaDeProblemas;
	}

	@Override
	public void handle(HttpServletRequest requisicao, HttpServletResponse resposta, AccessDeniedException excecao)
			throws IOException {
		this.fabricaDeProblemas.escreverNaResposta(
				this.fabricaDeProblemas.criar(CodigoErro.ACESSO_NEGADO, requisicao), resposta);
	}

}
