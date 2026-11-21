package cl.casol.backend;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP/JPA/JDBC reales con datos sintéticos aislados; no toca staging ni envía correos. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:chatbot_errores;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
        "jwt.secret=clave-sintetica-exclusiva-chatbot-busqueda-errores-123456789","jwt.expiration=3600000",
        "dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ChatbotBusquedaErroresHttpTest {
    static final java.util.concurrent.atomic.AtomicInteger CLIENTES = new java.util.concurrent.atomic.AtomicInteger();
    String cliente;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    static final String CONSULTA="Error al escribir el archivo /dim/tbk/2025 01 20/voucher-venta-original_49.txt";
    static final String REGISTRO="Error al escribir el archivo /dim/tbk/2026_06_10/voucher-venta-original_280.txt";

    @BeforeEach void datos() {
        // Casos independientes sin desactivar el límite real por cliente del endpoint público.
        cliente = "192.0.2." + CLIENTES.incrementAndGet();
        assertTrue(jdbc.queryForObject("select database()",String.class).toLowerCase().contains("chatbot_errores"));
        jdbc.execute("CREATE TABLE IF NOT EXISTS co_conocimiento_busqueda (conocimiento_id INT PRIMARY KEY, contenido_busqueda TEXT NOT NULL)");
        for(String tabla:new String[]{"co_conocimiento_busqueda","co_sintoma","co_solucion","co_conocimiento","u_usuario","u_rol"})
            jdbc.update("delete from "+tabla);
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'QA','qa@example.invalid','SIN_LOGIN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        conocimiento(1,REGISTRO,"PUBLICADO");
        solucion(10,1,"Resolver el incidente de escritura","Instrucciones públicas existentes",true,true,1);
        solucion(11,1,"hermanoprivadozx","Contenido reservado",true,false,2);
        conocimiento(3,"secretoborradorzx","BORRADOR");solucion(30,3,"secretoborradorzx","Privada",true,true,1);
        conocimiento(4,"secretoprivadozx","PUBLICADO");solucion(40,4,"secretoprivadozx","Privada",true,false,1);
        conocimiento(5,"secretoinactivozx","PUBLICADO");solucion(50,5,"secretoinactivozx","Inactiva",false,true,1);
        jdbc.update("insert into co_sintoma(sintoma_id,conocimiento_id,sintoma_descripcion,sintoma_orden) values(1,1,'sintomareservadozx',1)");
        jdbc.update("insert into co_conocimiento_busqueda values(1,'indicereservadozx')");
    }
    void conocimiento(int id,String titulo,String estado) {
        jdbc.update("insert into co_conocimiento(conocimiento_id,conocimiento_titulo,conocimiento_descripcion,conocimiento_comentario,conocimiento_estado,creado_por,fecha_creacion) values(?,?,'descripcionreservadazx','comentarioreservadozx',?,1,CURRENT_TIMESTAMP)",id,titulo,estado);
    }
    void solucion(int id,int conocimiento,String titulo,String descripcion,boolean activa,boolean publica,int orden) {
        jdbc.update("insert into co_solucion(solucion_id,conocimiento_id,solucion_titulo,solucion_descripcion,solucion_tipo,solucion_orden,solucion_estado,solucion_autoservicio) values(?,?,?,?,'PASOS',?,?,?)",id,conocimiento,titulo,descripcion,orden,activa,publica);
    }
    org.springframework.test.web.servlet.ResultActions consultar(String texto)throws Exception {
        return mvc.perform(post("/api/publico/chatbot/consultas").contentType(MediaType.APPLICATION_JSON)
                .with(request -> { request.setRemoteAddr(cliente); return request; })
                .content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(java.util.Map.of("consulta",texto))));
    }
    @ParameterizedTest @ValueSource(strings={REGISTRO,CONSULTA,
            "Error al escribir el archivo /dim/tbk/2024-12-31/voucher-venta-original_999.txt",
            "Error al escribir el archivo /dim/tbk/2025 01 20/voucher-copia_123.txt",
            "escribir voucher venta","ESCRIBIR voucher", "Error al escribir el archivo C:\\dim\\tbk\\2025 01 20\\voucher-venta-original_49.txt"})
    void encuentraErrorIdenticoFechasArchivosYPalabrasClave(String texto)throws Exception {
        consultar(texto).andExpect(status().isOk()).andExpect(jsonPath("$.soluciones.length()").value(1))
                .andExpect(jsonPath("$.soluciones[0].id").value(10))
                .andExpect(jsonPath("$.soluciones[0].solucion").value("Instrucciones públicas existentes"));
    }
    @Test void errorInexistenteOMensajeDeOtraOperacionNoInventaSolucion()throws Exception {
        for(String consulta:new String[]{"falloinexistentezx",CONSULTA.replace("escribir","leer"),"% _ al el"})
            consultar(consulta).andExpect(status().isOk()).andExpect(jsonPath("$.soluciones.length()").value(0));
    }
    @Test void coincidenciaExactaSePriorizaYElNumeroDeSolucionesNoAumentaLaRelevancia()throws Exception {
        conocimiento(2,"Caso general de soporte","PUBLICADO");
        for(int id=20;id<29;id++)solucion(id,2,"Alternativa",REGISTRO,true,true,id);
        conocimiento(6,CONSULTA,"PUBLICADO");solucion(60,6,"Solución exacta","Pasos existentes",true,true,1);
        consultar(CONSULTA).andExpect(status().isOk()).andExpect(jsonPath("$.soluciones[0].id").value(60))
                .andExpect(jsonPath("$.soluciones[1].id").value(10)).andExpect(jsonPath("$.soluciones.length()").value(5));
    }
    @Test void ordenaVariasSolucionesAsociadasYConservaAlternativasPublicas()throws Exception {
        solucion(12,1,"Validar Transbank","Pasos de Transbank",true,true,3);
        consultar("Transbank").andExpect(status().isOk()).andExpect(jsonPath("$.soluciones[0].id").value(12))
                .andExpect(jsonPath("$.soluciones[1].id").value(10));
    }
    @Test void codigosTecnicosDiferentesNoSonEquivalentes()throws Exception {
        conocimiento(6,"Error 0x0000011b","PUBLICADO");solucion(60,6,"Solución Windows","Pasos publicados",true,true,1);
        consultar("Error 0x0000011b").andExpect(status().isOk()).andExpect(jsonPath("$.soluciones[0].id").value(60));
        consultar("Error 0x0000011c").andExpect(status().isOk()).andExpect(jsonPath("$.soluciones.length()").value(0));
    }
    @ParameterizedTest @ValueSource(strings={"secretoborradorzx","secretoprivadozx","secretoinactivozx","hermanoprivadozx",
            "descripcionreservadazx","sintomareservadozx","comentarioreservadozx","indicereservadozx"})
    void camposInternosYContenidoNoAutorizadoNoGeneranCoincidencias(String consulta)throws Exception {
        consultar(consulta).andExpect(status().isOk()).andExpect(jsonPath("$.soluciones.length()").value(0));
    }
    @Test void consultaNoModificaContenidoNiIncluyeCamposInternosEnRespuesta()throws Exception {
        var antes=jdbc.queryForMap("select conocimiento_titulo,conocimiento_descripcion,conocimiento_comentario from co_conocimiento where conocimiento_id=1");
        String body=consultar(CONSULTA).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(antes,jdbc.queryForMap("select conocimiento_titulo,conocimiento_descripcion,conocimiento_comentario from co_conocimiento where conocimiento_id=1"));
        assertFalse(body.contains("reservado"));assertFalse(body.contains("conocimientoId"));
        assertEquals(4,jdbc.queryForObject("select count(*) from co_conocimiento",Integer.class));
    }
}
