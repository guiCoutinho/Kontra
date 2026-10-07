package br.com.kontra.comum.erro;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;

/**
 * Responde 400 {@link CodigoErro#REQUISICAO_MALFORMADA} (Problem Details) quando o firewall do Spring
 * Security ({@code StrictHttpFirewall}) rejeita a requisição — ex.: {@code ;}, {@code //} ou {@code ..}
 * codificado no caminho, ou método HTTP fora da lista permitida ({@code TRACE}, métodos desconhecidos).
 *
 * <p>Substitui o padrão do Spring Security, que faz {@code sendError(400)} e cairia no JSON do contêiner. O
 * Spring Security usa este bean automaticamente (único {@link RequestRejectedHandler} do contexto).
 */
@Component
public class TratadorRequisicaoRejeitada implements RequestRejectedHandler {

	static final String DETALHE = "A requisição foi rejeitada por conter caminho, caracteres ou método HTTP não permitidos.";

	private static final Logger log = LoggerFactory.getLogger(TratadorRequisicaoRejeitada.class);

	private final FabricaDeProblemas fabricaDeProblemas;

	public TratadorRequisicaoRejeitada(FabricaDeProblemas fabricaDeProblemas) {
		this.fabricaDeProblemas = fabricaDeProblemas;
	}

	@Override
	public void handle(HttpServletRequest requisicao, HttpServletResponse resposta, RequestRejectedException excecao)
			throws IOException {
		log.debug("Requisição rejeitada pelo firewall: {}", excecao.getMessage());
		// O firewall rejeita antes do HeaderWriterFilter: os cabeçalhos de segurança são escritos aqui.
		this.fabricaDeProblemas.aplicarCabecalhosDeSeguranca(resposta);
		this.fabricaDeProblemas.escreverNaResposta(this.fabricaDeProblemas.criar(HttpStatus.BAD_REQUEST,
				CodigoErro.REQUISICAO_MALFORMADA, DETALHE, requisicao), resposta);
	}

}
