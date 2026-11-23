package cl.casol.backend.whatsapp.infrastructure.busqueda;
import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.application.service.ProcesarReservaWhatsAppService;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import cl.casol.backend.whatsapp.infrastructure.persistence.TrabajadoresWhatsApp;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import static org.mockito.Mockito.*;
class CatalogoWorkerTest {
 @ParameterizedTest @ValueSource(booleans={true,false}) void noEnviaNiSimulaSiFallaPreparacion(boolean transitorio){
  var almacen=mock(AlmacenWhatsAppPort.class);var procesador=mock(ProcesarReservaWhatsAppService.class);var envio=mock(EnviarMensajeCanalPort.class);
  var salida=new AlmacenWhatsAppPort.Salida("salida","token",1,RespuestaCanal.simple(RespuestaCanal.Estado.RESULTADOS,"Resultado"));
  when(almacen.reservarSalida()).thenReturn(salida);var espera=Duration.ofSeconds(60);
  try(var cierre=new Cierre(new TrabajadoresWhatsApp(almacen,procesador,r->{throw new FalloCatalogoPublico(transitorio,espera);},null,null,envio))){
   cierre.worker.sondear();verify(almacen,timeout(2500)).reintentarSalida(salida,transitorio?5:1,espera);
   verifyNoInteractions(envio);verify(almacen,never()).simular(any(),any());verify(almacen,never()).aceptar(any(),any(),any());
  }
 }
 record Cierre(TrabajadoresWhatsApp worker) implements AutoCloseable {public void close(){worker.cerrar();}}
}
