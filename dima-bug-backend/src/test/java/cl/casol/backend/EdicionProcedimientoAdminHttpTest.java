package cl.casol.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:edicion_admin;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","jwt.secret=clave-sintetica-de-edicion-no-usar-en-produccion-123456789","jwt.expiration=3600000","dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000","dimabug.storage.materiales-dir=./target/test-edicion/materiales","dimabug.storage.tickets-dir=./target/test-edicion/tickets"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdicionProcedimientoAdminHttpTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    String token;
    String json(Object value){return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value);}
    String texto(ResultActions r)throws Exception{return r.andReturn().getResponse().getContentAsString();}
    int numero(String j,String path){return ((Number)JsonPath.read(j,path)).intValue();}
    ResultActions postear(String ruta,Object body)throws Exception{return mvc.perform(post(ruta).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json(body)));}
    ResultActions poner(String ruta,Object body)throws Exception{return mvc.perform(put(ruta).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json(body)));}
    ResultActions leer(String ruta)throws Exception{return mvc.perform(get(ruta).header("Authorization","Bearer "+token));}
    ResultActions parche(String ruta,Object body)throws Exception{return mvc.perform(patch(ruta).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json(body)));}
    ResultActions borrar(String ruta)throws Exception{return mvc.perform(delete(ruta).header("Authorization","Bearer "+token));}
    @BeforeAll void preparar()throws Exception {
        try(var c=jdbc.getDataSource().getConnection()){assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:edicion_admin"));}
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'Admin QA','qa-editor@example.invalid',?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",encoder.encode("SoloPrueba123!"));
        jdbc.update("insert into cl_sistema(sistema_id,sistema_nombre,sistema_estado) values(1,'Sistema QA',true)");
        jdbc.update("insert into cl_modulo(modulo_id,sistema_id,modulo_nombre,modulo_estado) values(1,1,'Módulo QA',true)");
        token=JsonPath.read(texto(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email","qa-editor@example.invalid","password","SoloPrueba123!")))).andExpect(status().isOk())),"$.token");
    }
    @ParameterizedTest @ValueSource(strings={"TECNICO","SOPORTE","USUARIO"}) void noAdministradoresNoModifican(String rol)throws Exception {
        for(var req:List.of(post("/api/procedimientos"),put("/api/procedimientos/1"),post("/api/procedimientos/1/edicion"),post("/api/procedimientos/1/pasos"),put("/api/procedimientos/1/pasos/1"),delete("/api/procedimientos/1/pasos/1"),patch("/api/procedimientos/1/pasos/1/orden"),put("/api/procedimientos/1/pasos/1/configuracion"),post("/api/procedimientos/1/pasos/1/materiales"),patch("/api/procedimientos/1/estado")))mvc.perform(req.with(user("qa").roles(rol)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/procedimientos/1/historial").with(user("qa").roles(rol))).andExpect(status().isForbidden());
    }
    @Test void revisionPermiteEditarTodoConservandoEjecucionesEvidenciasYArchivos()throws Exception {
        int id=numero(texto(postear("/api/procedimientos",Map.of("nombre","Guía original","descripcion","Antes","sistemaId",1,"moduloId",1,"informacionComplementaria","Original")).andExpect(status().isCreated())),"$.id");String ruta="/api/procedimientos/"+id;
        postear(ruta+"/pasos",Map.of("orden",1,"instruccion","Verificar instalaciones originales","esCritico",true,"modoEjecucion","SOLICITAR_EVIDENCIA")).andExpect(status().isCreated());
        postear(ruta+"/pasos",Map.of("orden",2,"instruccion","Paso manual original","esCritico",false,"modoEjecucion","MANUAL_GUIADO")).andExpect(status().isCreated());
        String originales=texto(leer(ruta+"/pasos").andExpect(status().isOk()));int pasoOriginal=numero(originales,"$[0].id");
        byte[] pdf="%PDF-1.4\nDocumento de prueba aislada\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String archivo=texto(mvc.perform(multipart(ruta+"/pasos/"+pasoOriginal+"/materiales/archivo").file(new MockMultipartFile("archivo","prueba.pdf","application/pdf",pdf)).param("nombre","Documento original").param("tipo","PDF").header("Authorization","Bearer "+token)).andExpect(status().isCreated()));int materialOriginal=numero(archivo,"$.id");
        parche(ruta+"/estado",Map.of("estado","PUBLICADO")).andExpect(status().isOk());
        int ejecucion=numero(texto(postear(ruta+"/ejecuciones",null).andExpect(status().isCreated())),"$.id");String er="/api/ejecuciones/"+ejecucion;
        int sucursal=numero(texto(postear("/api/admin/sucursales",Map.of("codigo","QA_EDIT","nombre","Sucursal QA","responsableNombre","Responsable QA","responsableEmail","qa-no-enviar@dimarsa.cl")).andExpect(status().isCreated())),"$.id");
        int ep=numero(texto(leer(er+"/pasos").andExpect(status().isOk())),"$[0].ejecucionPasoId");
        String solicitud=texto(postear(er+"/pasos/"+ep+"/correo",Map.of("sucursalId",sucursal,"destinatarios",List.of(Map.of("correo","qa-no-enviar@dimarsa.cl","tipo","PRINCIPAL")))).andExpect(status().isOk()));String sol=JsonPath.read(solicitud,"$.id");
        String recibida=texto(postear(er+"/solicitudes-evidencia/"+sol+"/simular-respuesta",Map.of("remitente","qa-no-enviar@dimarsa.cl","mensajeId",UUID.randomUUID().toString())).andExpect(status().isOk()));
        postear(er+"/solicitudes-evidencia/"+sol+"/decision",Map.of("version",numero(recibida,"$.version"),"aprobar",true)).andExpect(status().isOk());
        String viejos=texto(leer(er+"/pasos").andExpect(status().isOk()));int ep2=numero(viejos,"$[1].ejecucionPasoId");
        postear(er+"/pasos/"+ep2+"/siguiente",Map.of("versionEsperada",0,"confirmacionCritica",false)).andExpect(status().isOk());parche(er+"/completar",null).andExpect(status().isOk());
        int enCurso=numero(texto(postear(ruta+"/ejecuciones",null).andExpect(status().isCreated())),"$.id");
        jdbc.update("update se_ejecucion_paso set contrato_version=null where ejecucion_id=?",enCurso);
        String borrador=texto(postear(ruta+"/edicion",Map.of()).andExpect(status().isOk()));int nueva=numero(borrador,"$.id");assertNotEquals(id,nueva);assertEquals(2,numero(borrador,"$.revision"));assertEquals(nueva,numero(texto(postear(ruta+"/edicion",Map.of()).andExpect(status().isOk())),"$.id"));String nr="/api/procedimientos/"+nueva;
        poner(nr,Map.of("nombre","Guía revisada","descripcion","Después","sistemaId",1,"moduloId",1,"informacionComplementaria","Información nueva","estado","BORRADOR")).andExpect(status().isOk()).andExpect(jsonPath("$.sistemaId").value(1)).andExpect(jsonPath("$.moduloId").value(1));
        String copia=texto(leer(nr+"/pasos").andExpect(status().isOk()));int c1=numero(copia,"$[0].id"),c2=numero(copia,"$[1].id");assertNotEquals(pasoOriginal,c1);
        poner(nr+"/pasos/"+c1,Map.of("orden",1,"instruccion","Instrucción revisada","esCritico",false,"modoEjecucion","SOLICITAR_EVIDENCIA","configuracion",Map.of("evidenciasRequeridas",List.of("MUEBLE"),"requiereAprobacion",true,"asunto","Asunto revisado"))).andExpect(status().isOk()).andExpect(jsonPath("$.configuracion.evidenciasRequeridas.length()").value(1));
        parche(nr+"/pasos/"+c2+"/orden",Map.of("direccion","ARRIBA","ordenEsperado",List.of(c1,c2))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(c2));
        parche(nr+"/pasos/"+c1+"/orden",Map.of("direccion","ARRIBA","ordenEsperado",List.of(c1,c2))).andExpect(status().isConflict());
        int mc=numero(texto(leer(nr+"/pasos/"+c1+"/materiales").andExpect(status().isOk())),"$[0].id");borrar(nr+"/pasos/"+c1+"/materiales/"+mc).andExpect(status().isNoContent());
        mvc.perform(get(ruta+"/pasos/"+pasoOriginal+"/materiales/"+materialOriginal+"/archivo").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(content().bytes(pdf));
        postear(nr+"/pasos",Map.of("orden",3,"instruccion","Paso agregado","esCritico",true)).andExpect(status().isCreated());borrar(nr+"/pasos/"+c2).andExpect(status().isNoContent());
        parche(nr+"/estado",Map.of("estado","PUBLICADO")).andExpect(status().isOk());
        leer(ruta).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Guía original")).andExpect(jsonPath("$.vigente").value(false));
        // Una ejecución completada sigue enlazada a su procedimiento y paso originales.
        leer(er).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPLETADA")).andExpect(jsonPath("$.procedimientoId").value(id));
        leer(ruta+"/pasos/"+pasoOriginal+"/materiales").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(materialOriginal));
        leer(nr+"/pasos/"+c1+"/materiales").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        leer(er+"/pasos").andExpect(status().isOk()).andExpect(jsonPath("$[0].instruccion").value("Verificar instalaciones originales")).andExpect(jsonPath("$[0].esCritico").value(true));
        leer("/api/ejecuciones/"+enCurso+"/pasos").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].instruccion").value("Verificar instalaciones originales"));
        assertEquals(3,jdbc.queryForObject("select count(*) from se_evidencia where solicitud_id=?",Integer.class,sol));
        String auditoria=texto(leer(nr+"/historial").andExpect(status().isOk()));assertTrue(auditoria.contains("REVISION_CREADA"));assertTrue(auditoria.contains("PASO_MODIFICADO"));assertTrue(auditoria.contains("PASOS_REORDENADOS"));assertTrue(auditoria.contains("PASO_ELIMINADO"));assertTrue(auditoria.contains("REVISION_PUBLICADA"));assertFalse(auditoria.toLowerCase().contains("password"));
        assertTrue(jdbc.queryForObject("select count(*) from pr_auditoria_procedimiento where procedimiento_id=? and actor_id=1 and fecha is not null",Integer.class,nueva)>5);
        borrar(ruta+"/pasos/"+pasoOriginal).andExpect(status().isBadRequest());
        poner(ruta+"/pasos/"+pasoOriginal+"/configuracion",Map.of("modoEjecucion","SOLICITAR_EVIDENCIA","configuracion",Map.of("requiereAprobacion",true),"revisionConfiguracion","no-importa-en-publicado")).andExpect(status().isBadRequest());
    }
    @Test void crearRevisionEsIdempotenteAnteDosAdministradoresConcurrentes()throws Exception {
        int id=numero(texto(postear("/api/procedimientos",Map.of("nombre","Concurrente")).andExpect(status().isCreated())),"$.id");String r="/api/procedimientos/"+id;postear(r+"/pasos",Map.of("orden",1,"instruccion","Paso QA","esCritico",false)).andExpect(status().isCreated());parche(r+"/estado",Map.of("estado","PUBLICADO")).andExpect(status().isOk());
        var pool=Executors.newFixedThreadPool(2);try{Callable<Integer> crear=()->numero(texto(postear(r+"/edicion",Map.of()).andExpect(status().isOk())),"$.id");var a=pool.submit(crear);var b=pool.submit(crear);assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        assertEquals(2,jdbc.queryForObject("select count(*) from pr_procedimiento where procedimiento_nombre='Concurrente'",Integer.class));
    }
    @Test void guardarYPublicarConservaTodosLosDatosEnUnaOperacion()throws Exception {
        int id=numero(texto(postear("/api/procedimientos",Map.of("nombre","Antes de publicar")).andExpect(status().isCreated())),"$.id");String ruta="/api/procedimientos/"+id;
        postear(ruta+"/pasos",Map.of("orden",1,"instruccion","Paso de revisión","esCritico",false)).andExpect(status().isCreated());
        poner(ruta,Map.of("nombre","Datos publicados","descripcion","Descripción nueva","sistemaId",1,"moduloId",1,"informacionComplementaria","Complemento","estado","PUBLICADO")).andExpect(status().isOk());
        leer(ruta).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Datos publicados")).andExpect(jsonPath("$.informacionComplementaria").value("Complemento")).andExpect(jsonPath("$.sistemaId").value(1)).andExpect(jsonPath("$.moduloId").value(1)).andExpect(jsonPath("$.estado").value("PUBLICADO"));
    }
    @Test void publicarSinPasosRevierteLaModificacionGeneral()throws Exception {
        int id=numero(texto(postear("/api/procedimientos",Map.of("nombre","Borrador conservado")).andExpect(status().isCreated())),"$.id");String ruta="/api/procedimientos/"+id;
        poner(ruta,Map.of("nombre","Cambio no confirmado","estado","PUBLICADO")).andExpect(status().isBadRequest());
        leer(ruta).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Borrador conservado")).andExpect(jsonPath("$.estado").value("BORRADOR"));
        assertEquals(1,jdbc.queryForObject("select count(*) from pr_auditoria_procedimiento where procedimiento_id=?",Integer.class,id));
    }

}
