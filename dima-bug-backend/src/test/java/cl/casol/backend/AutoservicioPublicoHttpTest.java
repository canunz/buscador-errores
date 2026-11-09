package cl.casol.backend;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:autoservicio_publico;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","jwt.secret=clave-sintetica-solo-autoservicio-pruebas-123456789","jwt.expiration=3600000","dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000","dimabug.storage.materiales-dir=./target/test-autoservicio/materiales"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AutoservicioPublicoHttpTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
 @BeforeEach void datos(){
  assertTrue(jdbc.queryForObject("select database()",String.class).toLowerCase().contains("autoservicio_publico"));
  jdbc.execute("CREATE TABLE IF NOT EXISTS co_conocimiento_busqueda (conocimiento_id INT PRIMARY KEY, contenido_busqueda TEXT NOT NULL)");
  jdbc.update("delete from co_conocimiento_busqueda");
  for(String t:new String[]{"co_material_solucion","co_solucion","co_conocimiento","u_usuario","u_rol"})jdbc.update("delete from "+t);
  jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'QA','qa@example.invalid','NO_LOGIN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  for(int id:new int[]{1,2})jdbc.update("insert into co_conocimiento(conocimiento_id,conocimiento_titulo,conocimiento_descripcion,conocimiento_estado,creado_por,fecha_creacion) values(?, 'Titulo interno no publicar','Datos privados',?,1,CURRENT_TIMESTAMP)",id,id==1?"PUBLICADO":"BORRADOR");
  for(int id=1;id<=4;id++)jdbc.update("insert into co_solucion(solucion_id,conocimiento_id,solucion_descripcion,solucion_tipo,solucion_orden,solucion_titulo,solucion_estado,solucion_autoservicio) values(?,?,concat('Instrucción ',?),'PASOS',1,?, ?, ?)",id,id==4?2:1,id,id==1?"Reiniciar PDA":"INTERNA "+id,id!=3,id!=2);
  jdbc.update("insert into co_material_solucion(material_id,solucion_id,nombre,tipo,url,publicado) values(1,1,'Video autorizado','VIDEO','https://drive.google.com/file/d/qa/view',true),(2,1,'Adjunto privado','ENLACE','https://example.invalid/privado',false),(3,2,'Privado padre','ENLACE','https://example.invalid/recurso',true)");
 }
 @Test void unEnlaceIndividualNoDependeDeTenerSesionYNoExponeDatosAdministrativos()throws Exception {
  String anonimo=mvc.perform(get("/api/publico/soluciones/1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String autenticado=mvc.perform(get("/api/publico/soluciones/1").with(user("qa").roles("ADMINISTRADOR"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertEquals(anonimo,autenticado);assertFalse(anonimo.contains("conocimientoId"));assertFalse(anonimo.contains("Titulo interno"));
 }
 @Test void listadoYBusquedaAnonimosSoloContienenContenidoPublicable() throws Exception {
  mvc.perform(get("/api/publico/soluciones")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(1)).andExpect(jsonPath("$[0].conocimientoId").doesNotExist()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get("/api/publico/soluciones").param("q","PDA")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/publico/soluciones").param("q","INTERNA")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
 }
 @Test void idsPrivadosInactivosYBorradoresNoSeRevelan()throws Exception {
  for(int id:new int[]{2,3,4,999}){
   mvc.perform(get("/api/publico/soluciones/"+id)).andExpect(status().isNotFound());
   mvc.perform(get("/api/publico/soluciones/"+id+"/materiales")).andExpect(status().isNotFound());
  }
 }
 @Test void materialesRequierenPermisoPropioYPadrePublico()throws Exception {
  mvc.perform(get("/api/publico/soluciones/1/materiales")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Video autorizado"));
  mvc.perform(get("/api/publico/soluciones/1/materiales/2/archivo")).andExpect(status().isNotFound());
  mvc.perform(get("/api/publico/soluciones/2/materiales/3/archivo")).andExpect(status().isNotFound());
  mvc.perform(get("/api/conocimientos/1/materiales/2/archivo")).andExpect(status().isUnauthorized());
 }
 @Test void chatbotAnonimoNoExponeFuentesNiSolucionesInternas()throws Exception {
  String body=mvc.perform(post("/api/publico/chatbot/consultas").contentType(MediaType.APPLICATION_JSON).content("{\"consulta\":\"PDA\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.soluciones[0].titulo").value("Reiniciar PDA")).andExpect(jsonPath("$.soluciones[0].id").value(1)).andReturn().getResponse().getContentAsString();
  assertFalse(body.contains("INTERNA"));assertFalse(body.contains("Datos privados"));assertFalse(body.contains("Titulo interno"));
 }
 @Test void deshabilitarAutoservicioRetiraInmediatamenteDetalleMaterialYChatbot()throws Exception {
  mvc.perform(patch("/api/admin/conocimientos/1/soluciones/1/autoservicio").with(user("qa").roles("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content("{\"autoservicio\":false}")).andExpect(status().isOk());
  mvc.perform(get("/api/publico/soluciones/1")).andExpect(status().isNotFound());
  mvc.perform(get("/api/publico/soluciones/1/materiales")).andExpect(status().isNotFound());
  mvc.perform(post("/api/publico/chatbot/consultas").contentType(MediaType.APPLICATION_JSON).content("{\"consulta\":\"PDA\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.soluciones.length()").value(0));
  mvc.perform(patch("/api/admin/conocimientos/1/soluciones/1/autoservicio").with(user("qa").roles("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content("{\"autoservicio\":true}")).andExpect(status().isOk());
  mvc.perform(get("/api/publico/soluciones/1")).andExpect(status().isOk());
 }
 @Test void administracionYMutacionesPermanecenProtegidas()throws Exception {
  mvc.perform(post("/api/admin/soluciones/1/materiales").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
  mvc.perform(patch("/api/admin/conocimientos/1/soluciones/1/autoservicio").with(user("qa").roles("TECNICO")).contentType(MediaType.APPLICATION_JSON).content("{\"autoservicio\":false}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/publico/soluciones/1").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
 }
 @Test void cargaAutorizacionYDescargaDeArchivoPublicoConservanPrivacidad()throws Exception {
  byte[] pdf="%PDF-1.4\nQA publico\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  String body=mvc.perform(multipart("/api/admin/soluciones/1/materiales/archivo").file(new MockMultipartFile("archivo","qa.pdf","application/pdf",pdf)).param("nombre","Guía QA").param("tipo","PDF").with(user("qa").roles("ADMINISTRADOR"))).andExpect(status().isCreated()).andExpect(jsonPath("$.publicado").value(false)).andReturn().getResponse().getContentAsString();
  Number id=com.jayway.jsonpath.JsonPath.read(body,"$.id");String url="/api/publico/soluciones/1/materiales/"+id+"/archivo";
  mvc.perform(get(url)).andExpect(status().isNotFound());
  mvc.perform(patch("/api/admin/soluciones/1/materiales/"+id+"/publicacion").with(user("qa").roles("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":true}")).andExpect(status().isOk());
  mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(pdf)).andExpect(header().string("X-Content-Type-Options","nosniff"));
  mvc.perform(patch("/api/admin/soluciones/1/materiales/"+id+"/publicacion").with(user("qa").roles("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":false}")).andExpect(status().isOk());
  mvc.perform(get(url)).andExpect(status().isNotFound());
 }
 @Test void enlacesPeligrososYConsultaExcesivaSeRechazan()throws Exception {
  mvc.perform(get("/api/publico/soluciones").param("q","x".repeat(151))).andExpect(status().isBadRequest());
  for(String url:new String[]{"javascript:alert(1)","file:materiales/ajeno","http://127.0.0.1/privado","https://usuario:clave@example.invalid/x"})mvc.perform(post("/api/admin/soluciones/1/materiales").with(user("qa").roles("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"QA\",\"tipo\":\"ENLACE\",\"url\":\""+url+"\",\"publicado\":true}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/publico/chatbot/consultas").contentType(MediaType.APPLICATION_JSON).content("{\"consulta\":\""+"x".repeat(301)+"\"}")).andExpect(status().isBadRequest());
 }
}
