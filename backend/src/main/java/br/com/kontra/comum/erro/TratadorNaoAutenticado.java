package br.com.kontra.comum.erro;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Responde 401 {@link CodigoErro#NAO_AUTENTICADO} (Problem Details) quando uma rota protegida é acessada
 * sem autenticação. Roda no filtro do Spring Security, fora do Spring MVC.
 */
@Component
public class TratadorNaoAutenticado implements AuthenticationEntryPoint {

	private final FabricaDeProblemas fabricaDeProblemas;

	public TratadorNaoAutenticado(FabricaDeProblemas fabricaDeProblemas) {
		this.fabricaDeProblemas = fabricaDeProblemas;
	}

	@Override
	public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
			AuthenticationException excecao) throws IOException {
		this.fabricaDeProblemas.escreverNaResposta(
				this.fabricaDeProblemas.criar(CodigoErro.NAO_AUTENTICADO, requisicao), resposta);
	}

}
