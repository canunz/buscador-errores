package cl.casol.backend.shared.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DataSourceEnvironmentTest {
    // Solo comprueba binding de propiedades: no crea DataSource ni conecta a MySQL.
    private ApplicationContextRunner contexto(Map<String, Object> variables) {
        return new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("staging-test", variables)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class);
    }
    private Map<String,Object> entorno() {
        return new HashMap<>(Map.of("DB_URL", "jdbc:mysql://127.0.0.1:1/qa_mapping_no_connection",
                "DB_USERNAME", "usuario-ficticio-test", "DB_PASSWORD", "valor-ficticio-solo-test",
                "SPRING_PROFILES_ACTIVE", "staging"));
    }
    @Test void cargaRecursoYMapeaVariablesDelServidor() {
        contexto(entorno()).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            DataSourceProperties props = ctx.getBean(DataSourceProperties.class);
            assertThat(props.getUrl()).isEqualTo("jdbc:mysql://127.0.0.1:1/qa_mapping_no_connection");
            assertThat(props.getUsername()).isEqualTo("usuario-ficticio-test");
            assertThat(props.getPassword()).isEqualTo("valor-ficticio-solo-test");
            assertThat(ctx.getEnvironment().getActiveProfiles()).contains("staging");
            assertThat(ctx.containsBean("dataSource")).isFalse();
        });
    }
    @Test void variablesEstandarDeSpringConservanPrecedencia() {
        Map<String,Object> variables = entorno();
        variables.put("SPRING_DATASOURCE_URL", "jdbc:mysql://127.0.0.1:1/override_no_connection");
        contexto(variables).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(DataSourceProperties.class).getUrl()).isEqualTo("jdbc:mysql://127.0.0.1:1/override_no_connection");
        });
    }
    @Test void noHayUrlNiCredencialesPorDefectoCuandoFaltaElEntorno() {
        Map<String,Object> variables = entorno(); variables.remove("DB_URL");
        contexto(variables).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            // El binding puede conservar un placeholder sin resolver; no es una URL usable.
            assertThat(ctx.getBean(DataSourceProperties.class).getUrl()).isEqualTo("${DB_URL}");
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    ctx.getEnvironment().resolveRequiredPlaceholders("${DB_URL}"))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties(DataSourceProperties.class)
    static class Config { }
}
