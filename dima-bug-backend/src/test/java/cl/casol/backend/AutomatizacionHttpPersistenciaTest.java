package cl.casol.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP/JWT/servicios/JPA reales en un H2 nuevo. No usa usuarios, contraseñas o correo corporativos. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:automatizacion_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
    "jwt.secret=clave-sintetica-exclusiva-del-test-no-usar-en-produccion-12345","jwt.expiration=3600000",
    "dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000",
    "dimabug.storage.materiales-dir=./target/test-http/materiales","dimabug.storage.tickets-dir=./target/test-http/tickets"})
@ActiveProfiles("test") @AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AutomatizacionHttpPersistenciaTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean cl.casol.backend.ejecucion.application.service.GestionarEjecucionService gestionar;
    String token;
    ResultActions postJson(String path,String body)throws Exception{return mvc.perform(post(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));}
    String json(ResultActions resultado)throws Exception{return resultado.andReturn().getResponse().getContentAsString();}
    int numero(String json,String ruta){return ((Number)JsonPath.read(json,ruta)).intValue();}
    @Test void desdeLoginHastaAprobacionSinCasillaConPersistenciaReal()throws Exception {
        try(var connection=jdbc.getDataSource().getConnection()){assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:automatizacion_http"));}
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'QA HTTP','qa-http@example.invalid',?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",encoder.encode("ClaveSoloTest123!"));
        String login=json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"qa-http@example.invalid\",\"password\":\"ClaveSoloTest123!\"}")).andExpect(status().isOk()));token=JsonPath.read(login,"$.token");
        int sucursal=numero(json(postJson("/api/admin/sucursales","{\"codigo\":\"QA_HTTP\",\"nombre\":\"Sucursal sintética\",\"responsableNombre\":\"QA\",\"responsableEmail\":\"qa-no-enviar@dimarsa.cl\"}").andExpect(status().isCreated())),"$.id");
        int procedimiento=numero(json(postJson("/api/procedimientos","{\"nombre\":\"Preparar POS · HTTP\",\"descripcion\":\"Solo prueba aislada\"}").andExpect(status().isCreated())),"$.id");
        postJson("/api/procedimientos/"+procedimiento+"/pasos","{\"orden\":1,\"instruccion\":\"Verificar instalaciones\",\"esCritico\":true,\"modoEjecucion\":\"VALIDACION_EXTERNA\"}").andExpect(status().isCreated());
        postJson("/api/procedimientos/"+procedimiento+"/pasos","{\"orden\":2,\"instruccion\":\"Trabajo físico manual\",\"esCritico\":false}").andExpect(status().isCreated());
        mvc.perform(patch("/api/procedimientos/"+procedimiento+"/estado").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}")).andExpect(status().isOk());
        int ejecucion=numero(json(postJson("/api/procedimientos/"+procedimiento+"/ejecuciones","{\"sucursalId\":"+sucursal+",\"claveInicio\":\""+UUID.randomUUID()+"\"}").andExpect(status().isCreated())),"$.id");
        String ruta="/api/ejecuciones/"+ejecucion;
        String estado=json(mvc.perform(get(ruta+"/automatizacion").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.solicitudes[0].detalle.estado").value("ESPERANDO_EVIDENCIA")));
        String solicitud=JsonPath.read(estado,"$.solicitudes[0].detalle.id");String base=ruta+"/solicitudes-evidencia/"+solicitud;
        postJson(base+"/simular-tiempo","{\"horas\":24}").andExpect(status().isOk());
        var normalizador=new cl.casol.backend.ejecucion.infrastructure.automatizacion.FotosEvidenciaAdapter();
        var adjuntos=new java.util.ArrayList<org.springframework.mock.web.MockMultipartFile>();
        for(var categoria:java.util.List.of(cl.casol.backend.ejecucion.domain.EvidenciaFotografica.Categoria.MUEBLE,cl.casol.backend.ejecucion.domain.EvidenciaFotografica.Categoria.RED,cl.casol.backend.ejecucion.domain.EvidenciaFotografica.Categoria.ELECTRICIDAD))
            adjuntos.add(new org.springframework.mock.web.MockMultipartFile(categoria.name().toLowerCase(java.util.Locale.ROOT),"qa.jpg","image/jpeg",normalizador.muestra(categoria)));
        var anonima=multipart(base+"/evidencias");adjuntos.forEach(anonima::file);
        mvc.perform(anonima.param("remitente","qa-no-enviar@dimarsa.cl").param("mensajeId",UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
        var carga=multipart(base+"/evidencias");adjuntos.forEach(carga::file);
        String recepcion=json(mvc.perform(carga.header("Authorization","Bearer "+token).param("remitente","qa-no-enviar@dimarsa.cl").param("mensajeId",UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EVIDENCIA_RECIBIDA")));
        mvc.perform(get(ruta+"/pasos").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].cumplido").value(false));
        String recibida=json(mvc.perform(get(ruta+"/automatizacion").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.solicitudes[0].fotos.length()").value(3)).andExpect(jsonPath("$.solicitudes[0].fotos[0].datos").doesNotExist()));
        int foto=numero(recibida,"$.solicitudes[0].fotos[0].id");
        mvc.perform(get(base+"/fotos/"+foto).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/ejecuciones/avisos-evidencia").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].ruta").value("/ejecuciones/"+ejecucion));
        postJson(base+"/decision","{\"version\":"+numero(recepcion,"$.version")+",\"aprobar\":true}").andExpect(status().isOk());
        mvc.perform(get(ruta+"/pasos").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].cumplido").value(true)).andExpect(jsonPath("$[1].cumplido").value(false));
        assertEquals(3,jdbc.queryForObject("select count(*) from se_evidencia",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from se_evento_ejecucion where tipo='EVIDENCIA_APROBADA'",Integer.class));
        // Nuevo flujo: creación sin sucursal, envío dentro del paso y reintento idempotente.
        int diferida=numero(json(postJson("/api/procedimientos/"+procedimiento+"/ejecuciones","null").andExpect(status().isCreated())),"$.id");
        String nuevaRuta="/api/ejecuciones/"+diferida;
        mvc.perform(get(nuevaRuta+"/automatizacion").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.solicitudes.length()").value(0));
        String pasos=json(mvc.perform(get(nuevaRuta+"/pasos").header("Authorization","Bearer "+token)).andExpect(status().isOk()));
        int paso=numero(pasos,"$[0].ejecucionPasoId");
        String enviar=nuevaRuta+"/pasos/"+paso+"/correo";
        String datos="{\"sucursalId\":"+sucursal+",\"destinatarios\":[{\"correo\":\"qa-no-enviar@dimarsa.cl\",\"tipo\":\"PRINCIPAL\"}]}";
        mvc.perform(post(enviar).contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isUnauthorized());
        String enviada=json(postJson(enviar,datos).andExpect(status().isOk()).andExpect(jsonPath("$.fechaEnvio").isNotEmpty()).andExpect(jsonPath("$.estado").value("ESPERANDO_EVIDENCIA")));
        String repetida=json(postJson(enviar,datos).andExpect(status().isOk()));
        assertEquals((String)JsonPath.read(enviada,"$.id"),(String)JsonPath.read(repetida,"$.id"));
        mvc.perform(get(nuevaRuta+"/automatizacion").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.comunicaciones.length()").value(2)).andExpect(jsonPath("$.comunicaciones[0].pasoId").value(paso)).andExpect(jsonPath("$.comunicaciones[0].destinatarios[0].correo").value("qa-no-enviar@dimarsa.cl"));
        assertEquals(1,jdbc.queryForObject("select count(*) from se_solicitud_evidencia where ejecucion_id=?",Integer.class,diferida));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("Fallo sintético de BD")).when(gestionar).buscar(diferida,"qa-http@example.invalid");
        mvc.perform(get(nuevaRuta).header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable());
        org.mockito.Mockito.doCallRealMethod().when(gestionar).buscar(diferida,"qa-http@example.invalid");
        mvc.perform(get(nuevaRuta).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(diferida));


    }
}
