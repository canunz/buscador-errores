package cl.casol.backend.whatsapp.infrastructure.persistence;

import cl.casol.backend.whatsapp.infrastructure.meta.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class MetaQaConfiguracionTest {
 MockEnvironment env;
 @BeforeEach void entorno(){
  env=new MockEnvironment().withProperty("server.address","127.0.0.1").withProperty("server.forward-headers-strategy","none")
   .withProperty("spring.datasource.url","jdbc:h2:mem:wa_sintetico");env.setActiveProfiles("qa-whatsapp-meta");
 }
 void comprobar(){WhatsAppDurablePerfilGuard.validarPerfilDurable(env).postProcessBeanFactory(new DefaultListableBeanFactory());}
 @Test void perfilLocalEsAdmitido(){assertDoesNotThrow(this::comprobar);}
 @Test void otrosEntornosYPerfilesNoSeMezclan(){
  for(String otro:new String[]{"staging","prod","production","qa-whatsapp-http-local","qa-whatsapp-durable","qa-whatsapp-simulado"}){
   env.setActiveProfiles("qa-whatsapp-meta",otro);assertThrows(IllegalStateException.class,this::comprobar);
  }
 }
 @Test void envioRealYConexionEstanBloqueados(){
  for(String p:new String[]{"dimabug.whatsapp.meta-qa.envio-real","dimabug.whatsapp.meta-qa.conexion-meta"}){
   env.setProperty(p,"true");assertThrows(IllegalStateException.class,this::comprobar);env.setProperty(p,"false");
  }
 }
 @Test void envioRealSoloConAutoatencionAutorizada(){
  env.setProperty("dimabug.whatsapp.meta-qa.envio-real","true");env.setProperty("dimabug.whatsapp.meta-qa.conexion-meta","true");
  env.setProperty("dimabug.whatsapp.autoatencion.habilitada","true");assertThrows(IllegalStateException.class,this::comprobar);
  env.setProperty("dimabug.whatsapp.meta-qa.graph-token","EAAqa");assertThrows(IllegalStateException.class,this::comprobar);
  env.setProperty("dimabug.whatsapp.autoatencion.ticket-actor-email","bot.whatsapp@dimarsa.cl");assertDoesNotThrow(this::comprobar);
  env.setProperty("dimabug.whatsapp.autoatencion.habilitada","false");assertThrows(IllegalStateException.class,this::comprobar);
 }
 @Test void enlaceNoLocalYForwardedEstanBloqueados(){
  env.setProperty("server.address","0.0.0.0");assertThrows(IllegalStateException.class,this::comprobar);
  env.setProperty("server.address","127.0.0.1");env.setProperty("server.forward-headers-strategy","framework");assertThrows(IllegalStateException.class,this::comprobar);
 }
 @Test void mysqlExigeBaseExclusivaYValidate(){
  env.setProperty("spring.datasource.url","jdbc:mysql://127.0.0.1:13300/wa_durable_abc123?connectionTimeZone=UTC");
  env.setProperty("spring.jpa.hibernate.ddl-auto","validate");assertDoesNotThrow(this::comprobar);
  env.setProperty("spring.jpa.hibernate.ddl-auto","update");assertThrows(IllegalStateException.class,this::comprobar);
  env.setProperty("spring.jpa.hibernate.ddl-auto","validate");env.setProperty("spring.datasource.url","jdbc:mysql://127.0.0.1:3306/dima_bug");assertThrows(IllegalStateException.class,this::comprobar);
 }
 @Test void configuracionMetaSinAlmacenFallaAlArrancar(){
  env.setProperty("dimabug.whatsapp.meta-qa.waba","qa-waba");env.setProperty("dimabug.whatsapp.meta-qa.numero","qa-numero");
  env.setProperty("dimabug.whatsapp.meta-qa.participantes","tel:100000000000001=qa-a");
  env.setProperty("dimabug.whatsapp.meta-qa.verify-token","token-sintetico-meta-qa-local");env.setProperty("dimabug.whatsapp.meta-qa.app-secret","secreto-sintetico-meta-qa-local");env.setProperty("dimabug.whatsapp.qa.raiz","..");
  try(var contexto=new AnnotationConfigApplicationContext()){
   contexto.setEnvironment(env);contexto.register(MetaQaConfig.class);
   var e=assertThrows(org.springframework.beans.BeansException.class,contexto::refresh);
   assertTrue(String.valueOf(e.getMostSpecificCause().getMessage()).contains("AlmacenWhatsAppPort"));
  }
 }
 @Test void listaNoPermiteComodinesNiAliasDuplicados(){
  for(String aliases:new String[]{"","*","tel:100000000000001=*","tel:100000000000001=qa-a,tel:100000000000001=qa-b"})
   assertThrows(IllegalArgumentException.class,()->new PoliticaMetaQa("qa-waba","qa-numero",aliases));
 }
}
