package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.application.service.ProcesarMensajeService;
import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.domain.*;
import cl.casol.backend.whatsapp.infrastructure.simulado.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cl.casol.backend.whatsapp.domain.RespuestaCanal.Estado.*;

class WebhookSimuladoTest {
    BuscarSolucionesCanalPort buscar; ReconocerImagenPort ocr; Reloj reloj;
    WebhookSimuladoAdapter entrada; int secuencia;
    static class Reloj extends Clock {
        Instant ahora=Instant.parse("2026-11-13T12:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;}
        public Instant instant(){return ahora;}
    }
    @BeforeEach void preparar() {
        buscar=mock(BuscarSolucionesCanalPort.class);ocr=mock(ReconocerImagenPort.class);reloj=new Reloj();
        when(buscar.buscar(anyString())).thenReturn(List.of(new RespuestaCanal.Solucion(1,"Solución pública","Pasos existentes")));
        when(ocr.reconocer(anyString())).thenReturn(List.of(new RespuestaCanal.Candidato("Error OX0000011b",List.of("Revisa el código"))));
        entrada=new WebhookSimuladoAdapter(new ProcesarMensajeService(buscar,ocr,reloj));
    }
    public static byte[] evento(String id,String usuario,String tipo,String contenido) {
        var detalle=tipo.equals("text")?Map.of("body",contenido):Map.of("id",contenido,"mime_type","image/png");
        var mensaje=Map.of("from",usuario,"id",id,"timestamp","1794560400","type",tipo,tipo,detalle);
        return JsonMapper.builder().build().writeValueAsBytes(Map.of("object","whatsapp_business_account","entry",List.of(
            Map.of("id","qa-cuenta-a","changes",List.of(Map.of("field","messages","value",Map.of(
                "messaging_product","whatsapp","metadata",Map.of("phone_number_id","qa-canal-a"),"messages",List.of(mensaje))))))));
    }
    RespuestaCanal enviar(String usuario,String tipo,String contenido) {
        return entrada.recibir(evento("wamid.qa-"+(++secuencia),usuario,tipo,contenido)).getFirst();
    }
    RespuestaCanal texto(String t){return enviar("qa-usuario-a","text",t);}
    RespuestaCanal imagen(){return enviar("qa-usuario-a","image","qa-media-legible");}
    @Test void consultaValidaReutilizaPuerto(){assertEquals(RESULTADOS,texto("  Error de impresión  ").estado());verify(buscar).buscar("Error de impresión");}
    @Test void vacia(){assertEquals(INVALIDO,texto(" ").estado());verifyNoInteractions(buscar);}
    @Test void larga(){assertEquals(INVALIDO,texto("x".repeat(301)).estado());verifyNoInteractions(buscar);}
    @Test void sinResultados(){when(buscar.buscar(anyString())).thenReturn(List.of());assertEquals(SIN_RESULTADOS,texto("inexistente").estado());}
    @Test void imagenValidaNoBuscaYConservaAdvertencias(){var r=imagen();assertEquals(CONFIRMACION,r.estado());assertTrue(r.mensaje().contains("OX0000011b"));assertTrue(r.advertencias().contains("Revisa el código"));verifyNoInteractions(buscar);}
    @Test void sinTextoPermiteCorregir(){when(ocr.reconocer(anyString())).thenReturn(List.of());var r=imagen();assertEquals(CONFIRMACION,r.estado());var c=texto("CORREGIR "+r.referencia()+" Error manual");assertEquals(CONFIRMACION,c.estado());verifyNoInteractions(buscar);assertEquals(RESULTADOS,texto("CONFIRMAR "+c.referencia()).estado());}
    @Test void confirmaUnaVez(){var r=imagen();assertEquals(RESULTADOS,texto("CONFIRMAR "+r.referencia()).estado());assertEquals(INVALIDO,texto("CONFIRMAR "+r.referencia()).estado());verify(buscar,times(1)).buscar("Error OX0000011b");}
    @Test void correccionRequiereOtraConfirmacionEInvalidaReferenciaAnterior(){var r=imagen();var c=texto("CORREGIR "+r.referencia()+" Error corregido");verifyNoInteractions(buscar);assertEquals(INVALIDO,texto("CONFIRMAR "+r.referencia()).estado());texto("CONFIRMAR "+c.referencia());verify(buscar).buscar("Error corregido");}
    @Test void elegirOtroCandidato(){when(ocr.reconocer(anyString())).thenReturn(List.of(new RespuestaCanal.Candidato("Error A",List.of()),new RespuestaCanal.Candidato("HTTP 503",List.of())));var r=imagen();var c=texto("ELEGIR "+r.referencia()+" 2");texto("CONFIRMAR "+c.referencia());verify(buscar).buscar("HTTP 503");}
    @Test void cancela(){var r=imagen();assertEquals(CANCELADO,texto("CANCELAR "+r.referencia()).estado());assertEquals(INVALIDO,texto("CONFIRMAR "+r.referencia()).estado());verifyNoInteractions(buscar);}
    @Test void duplicadoNoRepiteNiTextoNiOcr(){byte[] e=evento("wamid.qa-unico","qa-usuario-a","image","qa-media-legible");entrada.recibir(e);assertEquals(DUPLICADO,entrada.recibir(e).getFirst().estado());verify(ocr,times(1)).reconocer(anyString());}
    @Test void incompletoYMalformado(){for(String json:List.of("{}","{","[]","null")){assertEquals(INVALIDO,entrada.recibir(json.getBytes(StandardCharsets.UTF_8)).getFirst().estado());}verifyNoInteractions(buscar,ocr);}
    @Test void tamañoMaximo(){assertEquals(INVALIDO,entrada.recibir(new byte[16385]).getFirst().estado());}
    @Test void tipoNoCompatible(){assertEquals(NO_COMPATIBLE,enviar("qa-usuario-a","audio","dato").estado());verifyNoInteractions(buscar,ocr);}
    @Test void usuariosAislados(){var a=imagen();var b=enviar("qa-usuario-b","image","qa-media-legible");assertEquals(INVALIDO,enviar("qa-usuario-b","text","CONFIRMAR "+a.referencia()).estado());assertEquals(RESULTADOS,enviar("qa-usuario-b","text","CONFIRMAR "+b.referencia()).estado());assertEquals(RESULTADOS,texto("CONFIRMAR "+a.referencia()).estado());}
    @Test void expira(){var r=imagen();reloj.ahora=reloj.ahora.plusSeconds(901);assertEquals(EXPIRADO,texto("CONFIRMAR "+r.referencia()).estado());verifyNoInteractions(buscar);}
    @Test void falloOcrControlado(){when(ocr.reconocer(anyString())).thenThrow(new IllegalStateException("dato sensible"));var r=imagen();assertEquals(ERROR_OCR,r.estado());assertFalse(r.mensaje().contains("sensible"));}
    @Test void timeout(){when(ocr.reconocer(anyString())).thenThrow(new ReconocerImagenPort.PlazoOcr());assertEquals(TIEMPO_AGOTADO,imagen().estado());}
    @Test void falloBuscadorConservaPropuesta(){var r=imagen();when(buscar.buscar(anyString())).thenThrow(new IllegalStateException("privado"));assertEquals(ERROR_BUSCADOR,texto("CONFIRMAR "+r.referencia()).estado());doReturn(List.of()).when(buscar).buscar(anyString());assertEquals(SIN_RESULTADOS,texto("CONFIRMAR "+r.referencia()).estado());}
    @Test void imagenNuevaInvalidaAnterior(){var a=imagen();var b=imagen();assertEquals(INVALIDO,texto("CONFIRMAR "+a.referencia()).estado());assertEquals(RESULTADOS,texto("CONFIRMAR "+b.referencia()).estado());}
    @Test void noAceptaTelefonosRealesNiRutasComoIdentificadores(){assertEquals(INVALIDO,entrada.recibir(evento("wamid.qa-a","56912345678","text","Error")).getFirst().estado());assertEquals(INVALIDO,enviar("qa-usuario-a","image","../../imagen").estado());verifyNoInteractions(buscar,ocr);}
    @Test void textoSinReferenciaNoConfirma(){imagen();assertEquals(CONFIRMACION,texto("sí").estado());verifyNoInteractions(buscar);}
    @Test void loteInvalidoNoProcesaParcialmente(){String json=new String(evento("wamid.qa-a","qa-usuario-a","text","Error"),StandardCharsets.UTF_8);json=json.replace("\"messages\":[", "\"messages\":[{},");assertEquals(INVALIDO,entrada.recibir(json.getBytes(StandardCharsets.UTF_8)).getFirst().estado());verifyNoInteractions(buscar);}

    @Test void conversacionesConcurrentes() throws Exception {
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->entrada.recibir(evento("wamid.qa-concurrente","qa-usuario-a","image","qa-media-legible")).getFirst());
            var b=pool.submit(()->entrada.recibir(evento("wamid.qa-concurrente","qa-usuario-b","image","qa-media-legible")).getFirst());
            var ra=a.get();var rb=b.get();assertNotEquals(ra.referencia(),rb.referencia());
            assertEquals(RESULTADOS,enviar("qa-usuario-a","text","CONFIRMAR "+ra.referencia()).estado());
            assertEquals(RESULTADOS,enviar("qa-usuario-b","text","CONFIRMAR "+rb.referencia()).estado());
        }
    }
    @Test void saturacionNoDesalojaIdsYVencimientoLiberaMemoria(){
        for(int n=0;n<1000;n++)entrada.recibir(evento("wamid.qa-"+n,"qa-usuario-a","audio","dato"));
        assertEquals(SATURADO,entrada.recibir(evento("wamid.qa-nuevo","qa-usuario-a","text","Error")).getFirst().estado());
        reloj.ahora=reloj.ahora.plusSeconds(3601);assertEquals(RESULTADOS,texto("Error").estado());
    }
    @Test void configuracionLimitadaAPerfilQaYAdaptadorSinEndpoint(){
        var perfil=WhatsAppQaConfig.class.getAnnotation(org.springframework.context.annotation.Profile.class);
        assertArrayEquals(new String[]{"qa-whatsapp-simulado"},perfil.value());
        assertNull(WebhookSimuladoAdapter.class.getAnnotation(org.springframework.web.bind.annotation.RestController.class));
        assertNull(WebhookSimuladoAdapter.class.getAnnotation(org.springframework.stereotype.Component.class));
    }

    @Test void rechazaClavesDuplicadasYJsonConcatenado(){
        byte[] original=evento("wamid.qa-a","qa-usuario-a","text","Error");
        String json=new String(original,StandardCharsets.UTF_8);
        assertEquals(INVALIDO,entrada.recibir((json+json).getBytes(StandardCharsets.UTF_8)).getFirst().estado());
        assertEquals(INVALIDO,entrada.recibir(json.replace("\"object\":", "\"object\":\"otro\",\"object\":").getBytes(StandardCharsets.UTF_8)).getFirst().estado());
        verifyNoInteractions(buscar,ocr);
    }

    @Test void fixturesDocumentados() throws Exception {
        var carpeta=java.nio.file.Path.of("../qa/whatsapp/fixtures");
        assertEquals(RESULTADOS,entrada.recibir(java.nio.file.Files.readAllBytes(carpeta.resolve("texto.json"))).getFirst().estado());
        assertEquals(CONFIRMACION,entrada.recibir(java.nio.file.Files.readAllBytes(carpeta.resolve("imagen.json"))).getFirst().estado());
        assertEquals(INVALIDO,entrada.recibir(java.nio.file.Files.readAllBytes(carpeta.resolve("incompleto.json"))).getFirst().estado());
        assertEquals(NO_COMPATIBLE,entrada.recibir(java.nio.file.Files.readAllBytes(carpeta.resolve("no-compatible.json"))).getFirst().estado());
    }
}
