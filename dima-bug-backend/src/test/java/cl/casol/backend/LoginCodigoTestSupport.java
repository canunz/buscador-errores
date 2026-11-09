package cl.casol.backend;

import cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort;
import com.jayway.jsonpath.JsonPath;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recorre el login HTTP real; solo sustituye el transporte de correo por un mock. */
final class LoginCodigoTestSupport {
    private LoginCodigoTestSupport() {}
    static String autenticar(MockMvc mvc, EnvioCodigoAccesoPort envio, String email, JdbcTemplate jdbc) throws Exception {
        // La columna de la migración de login se actualiza con SQL nativo y no pertenece al modelo JPA.
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")) {
                throw new IllegalStateException("Este fixture requiere H2 en memoria");
            }
        }
        jdbc.execute("ALTER TABLE u_usuario ADD COLUMN IF NOT EXISTS usuario_ultimo_ingreso TIMESTAMP NULL");
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        mvc.perform(post("/api/auth/codigo").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        var codigo = ArgumentCaptor.forClass(String.class);
        verify(envio).enviar(eq(email), anyString(), codigo.capture(), anyInt());
        String respuesta = mvc.perform(post("/api/auth/codigo/verificar").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "codigo", codigo.getValue()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        if (jdbc.queryForObject("select usuario_ultimo_ingreso from u_usuario where usuario_email = ?",
                java.sql.Timestamp.class, email) == null) {
            throw new AssertionError("El ingreso por código debe registrar la fecha de acceso");
        }
        return JsonPath.read(respuesta, "$.token");
    }
}
