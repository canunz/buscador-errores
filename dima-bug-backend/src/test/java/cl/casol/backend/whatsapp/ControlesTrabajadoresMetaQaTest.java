package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.application.service.ProcesarReservaWhatsAppService;
import cl.casol.backend.whatsapp.infrastructure.meta.*;
import cl.casol.backend.whatsapp.infrastructure.persistence.TrabajadoresWhatsApp;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControlesTrabajadoresMetaQaTest {
 AlmacenWhatsAppPort almacen;
 ControlesMetaQa controles;
 ObservabilidadMetaQa metricas;
 TrabajadoresWhatsApp trabajadores;
 @BeforeEach void preparar(){
  almacen=mock(AlmacenWhatsAppPort.class);metricas=new ObservabilidadMetaQa();
  controles=new ControlesMetaQa(new ControlesMetaQa.Estado(false,false,false,false),metricas);
  trabajadores=new TrabajadoresWhatsApp(almacen,mock(ProcesarReservaWhatsAppService.class),mock(PrepararRespuestaPublicaPort.class),controles,metricas);
 }
 @AfterEach void cerrar(){trabajadores.cerrar();}
 @Test void pausadosNoReservanPeroElMantenimientoContinua(){trabajadores.sondear();verifyNoInteractions(almacen);trabajadores.recuperar();verify(almacen).recuperar();}
 @Test void procesamientoNoHabilitaMediosNiSalida(){
  controles.procesamiento(true);trabajadores.sondear();verify(almacen,timeout(2000).atLeastOnce()).reservar(false);
  verify(almacen,never()).reservar(true);verify(almacen,never()).reservarSalida();
 }
 @Test void salidaSimuladaEsIndependienteDelProcesamiento(){
  controles.envio(true);trabajadores.sondear();verify(almacen,timeout(2000)).reservarSalida();verify(almacen,never()).reservar(anyBoolean());
 }
 @Test void mediosNecesitanTambienProcesamiento(){
  controles.medios(true);trabajadores.sondear();verifyNoInteractions(almacen);
  controles.procesamiento(true);trabajadores.sondear();verify(almacen,timeout(2000)).reservar(true);
 }
 @Test void falloDeMantenimientoTieneMetricaSegura(){
  doThrow(new IllegalStateException("NO_REGISTRAR_SQL_CREDENCIAL")).when(almacen).recuperar();
  assertDoesNotThrow(trabajadores::recuperar);assertEquals(1L,metricas.instantanea().get("RECUPERACION"));
 }
}
