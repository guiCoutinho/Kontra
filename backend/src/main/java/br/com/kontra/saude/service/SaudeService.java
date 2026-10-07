package br.com.kontra.saude.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import br.com.kontra.saude.dto.SaudeResponse;
import br.com.kontra.saude.dto.StatusComponente;

/**
 * Verificação de saúde da aplicação (ADR-008). No Marco 0 o único componente verificado é o banco de dados.
 */
@Service
public class SaudeService {

	/** Tempo máximo, em segundos, para o driver validar a conexão ({@link Connection#isValid(int)}). */
	static final int SEGUNDOS_PARA_VALIDAR_CONEXAO = 2;

	private static final Logger log = LoggerFactory.getLogger(SaudeService.class);

	private final DataSource dataSource;

	private final Clock relogio;

	/** Último status do banco, para registrar só as transições. Parte de disponível (como na subida da aplicação). */
	private final AtomicReference<StatusComponente> ultimoStatusDoBancoDeDados = new AtomicReference<>(
			StatusComponente.DISPONIVEL);

	@Autowired
	public SaudeService(DataSource dataSource) {
		this(dataSource, Clock.systemUTC());
	}

	SaudeService(DataSource dataSource, Clock relogio) {
		this.dataSource = dataSource;
		this.relogio = relogio;
	}

	/**
	 * Obtém uma conexão do pool e a valida. Com o banco fora, responde em poucos segundos: o pool desiste após o
	 * {@code connection-timeout} do Hikari (5 s) e a validação após {@value #SEGUNDOS_PARA_VALIDAR_CONEXAO} s.
	 */
	public SaudeResponse verificarBancoDeDados() {
		StatusComponente bancoDeDados = statusDoBancoDeDados();
		return new SaudeResponse(statusAgregado(List.of(bancoDeDados)), bancoDeDados,
				Instant.now(this.relogio).truncatedTo(ChronoUnit.MILLIS));
	}

	/** {@link StatusComponente#DISPONIVEL} se e somente se todos os componentes estão disponíveis. */
	static StatusComponente statusAgregado(List<StatusComponente> componentes) {
		return componentes.stream().allMatch(StatusComponente.DISPONIVEL::equals) ? StatusComponente.DISPONIVEL
				: StatusComponente.INDISPONIVEL;
	}

	/**
	 * Só {@link SQLException} significa banco indisponível; qualquer outra exceção propaga (vira 500
	 * {@code ERRO_INTERNO} no tratador global).
	 */
	private StatusComponente statusDoBancoDeDados() {
		StatusComponente status;
		String motivo = null;
		try (Connection conexao = this.dataSource.getConnection()) {
			status = conexao.isValid(SEGUNDOS_PARA_VALIDAR_CONEXAO) ? StatusComponente.DISPONIVEL
					: StatusComponente.INDISPONIVEL;
			if (status == StatusComponente.INDISPONIVEL) {
				motivo = "a conexão não respondeu à validação";
			}
		}
		catch (SQLException ex) {
			status = StatusComponente.INDISPONIVEL;
			// Só o tipo e o SQLState: a mensagem do driver pode trazer host, usuário ou outros detalhes.
			motivo = ex.getClass().getSimpleName() + ((ex.getSQLState() != null) ? ", SQLState " + ex.getSQLState() : "");
		}
		registrarMudancaDeStatus(status, motivo);
		return status;
	}

	/**
	 * Registra no log só as transições (WARN ao ficar indisponível, INFO ao voltar), e não cada verificação: o
	 * healthcheck do contêiner chama este endpoint a cada poucos segundos. {@link AtomicReference#getAndSet} garante
	 * que, com requisições simultâneas, uma única thread vê (e registra) cada transição.
	 */
	private void registrarMudancaDeStatus(StatusComponente atual, @Nullable String motivo) {
		StatusComponente anterior = this.ultimoStatusDoBancoDeDados.getAndSet(atual);
		if (anterior == atual) {
			if (atual == StatusComponente.INDISPONIVEL) {
				log.debug("Verificação de saúde: banco de dados continua indisponível ({}).", motivo);
			}
			return;
		}
		if (atual == StatusComponente.INDISPONIVEL) {
			log.warn("Verificação de saúde: banco de dados ficou indisponível ({}).", motivo);
		}
		else {
			log.info("Verificação de saúde: banco de dados voltou a ficar disponível.");
		}
	}

}
