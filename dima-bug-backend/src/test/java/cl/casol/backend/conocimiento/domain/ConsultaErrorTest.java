package cl.casol.backend.conocimiento.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsultaErrorTest {
    static final String CONSULTA = "Error al escribir el archivo /dim/tbk/2025 01 20/voucher-venta-original_49.txt";
    static final String REGISTRO = "Error al escribir el archivo /dim/tbk/2026_06_10/voucher-venta-original_280.txt";

    @Test void fechasYSufijosVariablesCompartenFirmaSinModificarOriginales() {
        var a = new ConsultaError(CONSULTA); var b = new ConsultaError(REGISTRO);
        assertEquals(a.firma(), b.firma());
        assertTrue(a.firma().contains("dim tbk voucher venta original txt"));
        assertTrue(a.codigos().isEmpty());
        assertTrue(a.original().contains("2025 01 20"));
        assertTrue(a.puntuar(REGISTRO,"Preparar Transbank","Instrucción publicada") > 600);
    }
    @Test void identidadGanaASimilitudYTituloGanaADescripcion() {
        var consulta = new ConsultaError(CONSULTA);
        assertTrue(consulta.puntuar(CONSULTA,"Solución","Pasos") > consulta.puntuar(REGISTRO,"Solución","Pasos"));
        assertTrue(consulta.puntuar(REGISTRO,"Solución","Pasos") > consulta.puntuar("Caso","Solución",REGISTRO));
    }
    @Test void rutasWindowsYUnixYSeparadoresDeFechaSonCompatibles() {
        assertEquals(new ConsultaError(REGISTRO).firma(),new ConsultaError(REGISTRO.replace('/', '\\').replace("2026_06_10","2026-06-10")).firma());
    }
    @Test void codigosTecnicosNoSeBorranNiSeSustituyenPorOtros() {
        for (String codigo : new String[]{"0x0000011b","404","ORA-00942","SQLSTATE-42S22","9090"}) {
            var c = new ConsultaError("Error " + codigo);
            assertTrue(c.codigos().contains(codigo.toLowerCase()));
            assertTrue(c.puntuar("Error " + codigo,"Solución","Instrucciones") > 0);
            assertEquals(0,c.puntuar("Error " + codigo + "9","Solución","Instrucciones"));
        }
        assertEquals(0,new ConsultaError("Error 404").puntuar("Error 500","Solución","Pasos"));
        assertTrue(new ConsultaError("ORA_00942.log").codigos().contains("00942"));
    }
    @Test void palabrasClaveYParcialesSinFechaPuedenCoincidir() {
        assertTrue(new ConsultaError("escribir voucher venta").puntuar(REGISTRO,"Solución","Pasos") > 0);
        assertTrue(new ConsultaError("configur impresora").puntuar("Configurar impresora","Solución","Pasos") > 0);
    }
    @Test void ruidoNoSeConvierteEnUnListadoPublicoCompleto() {
        assertEquals(0,new ConsultaError("error inexistentezx").puntuar(REGISTRO,"Solución","Pasos"));
        assertTrue(new ConsultaError("% _ ! al el").terminos().isEmpty());
        assertTrue(new ConsultaError(null).terminos().isEmpty());
        assertEquals(0,new ConsultaError(CONSULTA.replace("escribir","leer")).puntuar(REGISTRO,"Solución","Pasos"));
    }
    @Test void noSeEliminanNegacionesYSeNormalizanAcentos() {
        assertTrue(new ConsultaError("No imprime sin conexión").terminos().contains("no"));
        assertTrue(new ConsultaError("No imprime sin conexión").terminos().contains("sin"));
        assertEquals(new ConsultaError("CONFIGURACIÓN").firma(),new ConsultaError("configuracion").firma());
    }
}
