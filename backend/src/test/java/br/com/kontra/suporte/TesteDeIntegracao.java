package br.com.kontra.suporte;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Configuração única dos testes de integração ({@code *IT}): aplicação completa num servidor real (porta
 * aleatória), {@code MockMvc} disponível e PostgreSQL 18 do {@link TestcontainersConfig}.
 *
 * <p>Todos os {@code *IT} devem usar esta anotação (e nada além dela que mude o contexto): assim o cache de
 * contexto do Spring reaproveita a mesma aplicação e o mesmo container em toda a execução (ADR-011).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public @interface TesteDeIntegracao {
}
