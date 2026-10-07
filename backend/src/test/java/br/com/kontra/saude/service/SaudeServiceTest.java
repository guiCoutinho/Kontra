package br.com.kontra.saude.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import br.com.kontra.saude.dto.SaudeResponse;
import br.com.kontra.saude.dto.StatusComponente;

@ExtendWith(MockitoExtension.class)
class SaudeServiceTest {

	private static final Instant AGORA = Instant.parse("2026-10-06T21:00:00.123456Z");

	@Mock
	private DataSource dataSource;

	@Mock
	private Connection conexao;

	private SaudeService saudeService;

	private final ListAppender<ILoggingEvent> logCapturado = new ListAppender<>();

	private final Logger logDoServico = (Logger) LoggerFactory.getLogger(SaudeService.class);

	private Level nivelOriginalDoLog;

	@BeforeEach
	void criarServico() {
		this.saudeService = new SaudeService(this.dataSource, Clock.fixed(AGORA, ZoneOffset.UTC));
		this.nivelOriginalDoLog = this.logDoServico.getLevel();
		this.logDoServico.setLevel(Level.DEBUG);
		this.logCapturado.start();
		this.logDoServico.addAppender(this.logCapturado);
	}

	@AfterEach
	void restaurarLog() {
		this.logDoServico.detachAppender(this.logCapturado);
		this.logDoServico.setLevel(this.nivelOriginalDoLog);
	}

	/** Eventos do log do serviço no nível exato informado. */
	private List<ILoggingEvent> eventosDeLog(Level nivel) {
		return this.logCapturado.list.stream().filter(evento -> evento.getLevel().equals(nivel)).toList();
	}

	@Test
	void deveInformarTudoDisponivelQuandoConexaoForValida() throws Exception {
		given(this.dataSource.getConnection()).willReturn(this.conexao);
		given(this.conexao.isValid(SaudeService.SEGUNDOS_PARA_VALIDAR_CONEXAO)).willReturn(true);

		SaudeResponse saude = this.saudeService.verificarBancoDeDados();

		assertThat(saude).isEqualTo(new SaudeResponse(StatusComponente.DISPONIVEL, StatusComponente.DISPONIVEL,
				Instant.parse("2026-10-06T21:00:00.123Z")));
		verify(this.conexao).isValid(2);
		verify(this.conexao).close();
	}

	@Test
	void deveInformarIndisponivelQuandoObterConexaoLancarSQLException() throws Exception {
		given(this.dataSource.getConnection())
			.willThrow(new SQLTransientConnectionException("HikariPool-1 - Connection is not available"));

		SaudeResponse saude = this.saudeService.verificarBancoDeDados();

		assertThat(saude.status()).isEqualTo(StatusComponente.INDISPONIVEL);
		assertThat(saude.bancoDeDados()).isEqualTo(StatusComponente.INDISPONIVEL);
		assertThat(saude.verificadoEm()).isEqualTo(Instant.parse("2026-10-06T21:00:00.123Z"));
	}

	@Test
	void deveInformarIndisponivelEFecharConexaoQuandoValidacaoRetornarFalso() throws Exception {
		given(this.dataSource.getConnection()).willReturn(this.conexao);
		given(this.conexao.isValid(SaudeService.SEGUNDOS_PARA_VALIDAR_CONEXAO)).willReturn(false);

		SaudeResponse saude = this.saudeService.verificarBancoDeDados();

		assertThat(saude.status()).isEqualTo(StatusComponente.INDISPONIVEL);
		assertThat(saude.bancoDeDados()).isEqualTo(StatusComponente.INDISPONIVEL);
		verify(this.conexao).close();
	}

	@Test
	void deveInformarIndisponivelEFecharConexaoQuandoValidacaoLancarSQLException() throws Exception {
		given(this.dataSource.getConnection()).willReturn(this.conexao);
		given(this.conexao.isValid(SaudeService.SEGUNDOS_PARA_VALIDAR_CONEXAO))
			.willThrow(new SQLException("conexão encerrada"));

		SaudeResponse saude = this.saudeService.verificarBancoDeDados();

		assertThat(saude.status()).isEqualTo(StatusComponente.INDISPONIVEL);
		assertThat(saude.bancoDeDados()).isEqualTo(StatusComponente.INDISPONIVEL);
		verify(this.conexao).close();
	}

	@Test
	void devePropagarExcecaoSemInformarIndisponivelQuandoObterConexaoLancarRuntimeException() throws Exception {
		given(this.dataSource.getConnection()).willThrow(new IllegalStateException("pool encerrado"));

		assertThatIllegalStateException().isThrownBy(() -> this.saudeService.verificarBancoDeDados())
			.withMessage("pool encerrado");
		assertThat(eventosDeLog(Level.WARN)).isEmpty();
		assertThat(eventosDeLog(Level.INFO)).isEmpty();
	}

	@Test
	void deveRegistrarUmUnicoWarnSemDetalhesQuandoBancoFalharEmDuasVerificacoesSeguidas() throws Exception {
		given(this.dataSource.getConnection()).willThrow(
				new SQLException("FATAL: password authentication failed for user \"kontra\" em banco:5432", "28P01"));

		this.saudeService.verificarBancoDeDados();
		this.saudeService.verificarBancoDeDados();

		List<ILoggingEvent> avisos = eventosDeLog(Level.WARN);
		assertThat(avisos).hasSize(1);
		ILoggingEvent aviso = avisos.getFirst();
		assertThat(aviso.getFormattedMessage())
			.isEqualTo("Verificação de saúde: banco de dados ficou indisponível (SQLException, SQLState 28P01).")
			.doesNotContain("password", "kontra", "5432");
		assertThat(aviso.getThrowableProxy()).as("stack trace no log").isNull();
	}

	@Test
	void deveRegistrarUmInfoQuandoBancoVoltarAFicarDisponivel() throws Exception {
		given(this.dataSource.getConnection()).willThrow(new SQLTransientConnectionException("timeout"))
			.willThrow(new SQLTransientConnectionException("timeout"))
			.willReturn(this.conexao);
		given(this.conexao.isValid(SaudeService.SEGUNDOS_PARA_VALIDAR_CONEXAO)).willReturn(true);

		for (int i = 0; i < 4; i++) {
			this.saudeService.verificarBancoDeDados();
		}

		assertThat(eventosDeLog(Level.WARN)).extracting(ILoggingEvent::getFormattedMessage)
			.containsExactly(
					"Verificação de saúde: banco de dados ficou indisponível (SQLTransientConnectionException).");
		assertThat(eventosDeLog(Level.INFO)).extracting(ILoggingEvent::getFormattedMessage)
			.containsExactly("Verificação de saúde: banco de dados voltou a ficar disponível.");
	}

	@Test
	void deveNaoRegistrarNadaQuandoBancoContinuarDisponivel() throws Exception {
		given(this.dataSource.getConnection()).willReturn(this.conexao);
		given(this.conexao.isValid(SaudeService.SEGUNDOS_PARA_VALIDAR_CONEXAO)).willReturn(true);

		this.saudeService.verificarBancoDeDados();
		this.saudeService.verificarBancoDeDados();

		assertThat(eventosDeLog(Level.WARN)).isEmpty();
		assertThat(eventosDeLog(Level.INFO)).isEmpty();
	}

	@Test
	void deveRegistrarUmUnicoWarnQuandoVerificacoesSimultaneasEncontraremBancoIndisponivel() throws Exception {
		given(this.dataSource.getConnection()).willThrow(new SQLTransientConnectionException("timeout"));
		int threads = 8;
		int verificacoesPorThread = 50;
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<?>> tarefas = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				tarefas.add(executor.submit(() -> {
					largada.await();
					for (int i = 0; i < verificacoesPorThread; i++) {
						this.saudeService.verificarBancoDeDados();
					}
					return null;
				}));
			}
			largada.countDown();
			for (Future<?> tarefa : tarefas) {
				tarefa.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(eventosDeLog(Level.WARN)).as("WARN em %d verificações simultâneas", threads * verificacoesPorThread)
			.hasSize(1);
	}

	@Test
	void deveAgregarComoDisponivelSomenteQuandoTodosOsComponentesEstiveremDisponiveis() {
		assertThat(SaudeService.statusAgregado(List.of(StatusComponente.DISPONIVEL, StatusComponente.DISPONIVEL)))
			.isEqualTo(StatusComponente.DISPONIVEL);
		assertThat(SaudeService.statusAgregado(List.of(StatusComponente.DISPONIVEL, StatusComponente.INDISPONIVEL)))
			.isEqualTo(StatusComponente.INDISPONIVEL);
		assertThat(SaudeService.statusAgregado(List.of(StatusComponente.INDISPONIVEL)))
			.isEqualTo(StatusComponente.INDISPONIVEL);
	}

}
