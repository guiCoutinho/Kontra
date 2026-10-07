package br.com.kontra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import br.com.kontra.comum.erro.FabricaDeProblemas;
import br.com.kontra.comum.erro.TratadorAcessoNegado;
import br.com.kontra.comum.erro.TratadorNaoAutenticado;
import br.com.kontra.comum.erro.TratadorRequisicaoRejeitada;

/**
 * Spring Security no Marco 0 (ADR-007): sem mecanismo de autenticação e negando por padrão.
 *
 * <ul>
 * <li>públicas: {@code GET /api/saude}, documentação da API ({@code /v3/api-docs/**}, Swagger UI) e
 * {@code /error};
 * <li>todas as demais rotas (inclusive inexistentes) exigem autenticação → 401 {@code NAO_AUTENTICADO};
 * <li>sem sessão, CSRF, HTTP Basic, formulário de login e logout. O usuário em memória gerado pelo Boot
 * está desligado em {@code application.yml} ({@code spring.autoconfigure.exclude}).
 * </ul>
 *
 * <p>O marco de autenticação deve reavaliar obrigatoriamente o CSRF. Toda rota pública nova exige alteração
 * explícita desta classe.
 *
 * <p>Requisições rejeitadas pelo firewall do Spring Security respondem 400 em Problem Details pelo
 * {@link TratadorRequisicaoRejeitada} (bean usado automaticamente pelo Spring Security).
 *
 * <p>Importa os tratadores (401, 403, firewall) e a fábrica de problemas para que testes de fatia
 * ({@code @WebMvcTest} + {@code @Import(SegurancaConfig.class)}) tenham tudo de que a cadeia precisa; na
 * aplicação eles já são registrados pela varredura de componentes (o Spring não os duplica).
 */
@Configuration(proxyBeanMethods = false)
@Import({ FabricaDeProblemas.class, TratadorNaoAutenticado.class, TratadorAcessoNegado.class,
		TratadorRequisicaoRejeitada.class })
public class SegurancaConfig {

	@Bean
	SecurityFilterChain cadeiaDeFiltrosDeSeguranca(HttpSecurity http, TratadorNaoAutenticado tratadorNaoAutenticado,
			TratadorAcessoNegado tratadorAcessoNegado) throws Exception {
		return http
			.authorizeHttpRequests(autorizacao -> autorizacao
				.requestMatchers(HttpMethod.GET, "/api/saude").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/error").permitAll()
				.anyRequest().authenticated())
			.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.exceptionHandling(excecoes -> excecoes
				.authenticationEntryPoint(tratadorNaoAutenticado)
				.accessDeniedHandler(tratadorAcessoNegado))
			.build();
	}

}
