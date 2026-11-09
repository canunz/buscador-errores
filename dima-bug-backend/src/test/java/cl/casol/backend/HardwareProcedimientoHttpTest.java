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
import org.springframework.http.MediaType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:hardware_procedimiento;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","jwt.secret=clave-sintetica-hardware-procedimiento-solo-test-123456789","jwt.expiration=3600000","dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HardwareProcedimientoHttpTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;String token;
    String json(Object v){return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(v);}
    String texto(ResultActions r)throws Exception{return r.andReturn().getResponse().getContentAsString();}
    int id(String r){return ((Number)JsonPath.read(r,"$.id")).intValue();}
    ResultActions crear(Object v)throws Exception{return mvc.perform(post("/api/procedimientos").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json(v)));}
    ResultActions editar(int id,Object v)throws Exception{return mvc.perform(put("/api/procedimientos/"+id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json(v)));}
    ResultActions leer(int id)throws Exception{return mvc.perform(get("/api/procedimientos/"+id).header("Authorization","Bearer "+token));}
    @BeforeAll void datos()throws Exception{
        try(var c=jdbc.getDataSource().getConnection()){assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:hardware_procedimiento"));}
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'Admin hardware QA','qa-hardware@example.invalid',?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",encoder.encode("SoloPruebas123!"));
        jdbc.update("insert into cl_hardware(hardware_id,hardware_nombre,hardware_estado) values(1,'POS de venta QA',true),(2,'Impresora QA',true),(3,'Hardware legado QA',false)");
        jdbc.update("insert into cl_sistema(sistema_id,sistema_nombre,sistema_estado) values(1,'Sistema conservado',true)");
        jdbc.update("insert into cl_modulo(modulo_id,sistema_id,modulo_nombre,modulo_estado) values(1,1,'Módulo conservado',true)");
        token=JsonPath.read(texto(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email","qa-hardware@example.invalid","password","SoloPruebas123!")))).andExpect(status().isOk())),"$.token");
    }
    @AfterAll void noDuplicaCatalogo(){assertEquals(3,jdbc.queryForObject("select count(*) from cl_hardware",Integer.class));}
    @Test void creaConHardwareYLoRecupera()throws Exception{int p=id(texto(crear(Map.of("nombre","Con POS","hardwareId",1)).andExpect(status().isCreated()).andExpect(jsonPath("$.hardwareId").value(1))));leer(p).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(1));assertEquals(1,jdbc.queryForObject("select hardware_id from pr_procedimiento where procedimiento_id=?",Integer.class,p));}
    @Test void creaSinHardwareConNullYConClienteAnterior()throws Exception{var datos=new LinkedHashMap<String,Object>();datos.put("nombre","General sin hardware");datos.put("hardwareId",null);crear(datos).andExpect(status().isCreated()).andExpect(jsonPath("$.hardwareId").isEmpty());crear(Map.of("nombre","Cliente anterior")).andExpect(status().isCreated()).andExpect(jsonPath("$.hardwareId").isEmpty());}
    @Test void permiteCambiarYQuitarHardwarePeroOmitirloLoConserva()throws Exception{
        int p=id(texto(crear(Map.of("nombre","Edición hardware","hardwareId",1)).andExpect(status().isCreated())));
        editar(p,Map.of("nombre","Con impresora","hardwareId",2,"estado","BORRADOR")).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(2));
        editar(p,Map.of("nombre","Cliente legado conserva","estado","BORRADOR")).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(2));
        var datos=new LinkedHashMap<String,Object>();datos.put("nombre","General editado");datos.put("hardwareId",null);datos.put("estado","BORRADOR");editar(p,datos).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").isEmpty());leer(p).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={-1,0,999999}) void rechazaHardwareInvalidoSinCrearProcedimiento(int hardware)throws Exception{int antes=jdbc.queryForObject("select count(*) from pr_procedimiento",Integer.class);crear(Map.of("nombre","No debe crearse","hardwareId",hardware)).andExpect(status().isBadRequest());assertEquals(antes,jdbc.queryForObject("select count(*) from pr_procedimiento",Integer.class));}
    @Test void conservaSistemaYModuloLegadosAunqueYaNoSeEditen()throws Exception{
        int p=id(texto(crear(Map.of("nombre","Asociación anterior","sistemaId",1,"moduloId",1)).andExpect(status().isCreated())));
        jdbc.update("update cl_sistema set sistema_estado=false where sistema_id=1");jdbc.update("update cl_modulo set modulo_estado=false where modulo_id=1");
        try{editar(p,Map.of("nombre","Ahora con hardware","hardwareId",1,"estado","BORRADOR")).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(1)).andExpect(jsonPath("$.sistemaId").value(1)).andExpect(jsonPath("$.moduloId").value(1));}finally{jdbc.update("update cl_sistema set sistema_estado=true where sistema_id=1");jdbc.update("update cl_modulo set modulo_estado=true where modulo_id=1");}
    }
    @Test void hardwareLegadoInactivoNoSePierdeAlEditar()throws Exception{int p=id(texto(crear(Map.of("nombre","Hardware existente inactivo","hardwareId",3)).andExpect(status().isCreated())));leer(p).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(3));editar(p,Map.of("nombre","Conserva asociación","hardwareId",3,"estado","BORRADOR")).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(3));}
    @Test void revisionYPublicacionConservanElHardwareHistorico()throws Exception{
        int original=id(texto(crear(Map.of("nombre","Versión hardware","hardwareId",1)).andExpect(status().isCreated())));String r="/api/procedimientos/"+original;
        mvc.perform(post(r+"/pasos").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("orden",1,"instruccion","Paso de prueba","esCritico",false)))).andExpect(status().isCreated());
        mvc.perform(patch(r+"/estado").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}")).andExpect(status().isOk());
        int ejecucion=id(texto(mvc.perform(post(r+"/ejecuciones").header("Authorization","Bearer "+token)).andExpect(status().isCreated())));
        editar(original,Map.of("nombre","Cambio directo prohibido","hardwareId",2)).andExpect(status().isBadRequest());
        int copia=id(texto(mvc.perform(post(r+"/edicion").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(1))));
        editar(copia,Map.of("nombre","Nueva versión hardware","hardwareId",2,"estado","PUBLICADO")).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(2));
        leer(original).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(1));leer(copia).andExpect(status().isOk()).andExpect(jsonPath("$.hardwareId").value(2));
        mvc.perform(get("/api/ejecuciones/"+ejecucion).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.procedimientoId").value(original));
    }
    @ParameterizedTest @ValueSource(strings={"TECNICO","SOPORTE","USUARIO"}) void soloAdministradorModificaAsociacion(String rol)throws Exception{String datos=json(Map.of("nombre","Sin permiso","hardwareId",1));mvc.perform(post("/api/procedimientos").with(user("qa").roles(rol)).contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isForbidden());mvc.perform(put("/api/procedimientos/1").with(user("qa").roles(rol)).contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isForbidden());}
}
