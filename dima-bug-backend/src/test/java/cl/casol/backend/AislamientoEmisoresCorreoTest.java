package cl.casol.backend;

import cl.casol.backend.correo.application.port.out.*;
import cl.casol.backend.identidad.api.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.application.service.DespacharCodigosService;
import cl.casol.backend.identidad.domain.CodigoAcceso;
import cl.casol.backend.identidad.infrastructure.correo.*;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.infrastructure.integracion.AvisoTicketCriticoCorreoAdapter;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No Spring Boot server, database, SMTP or Google: only actual emitters and mocked ports. */
class AislamientoEmisoresCorreoTest {
    private String asunto(String raw) {
        try {
            return new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw))).getSubject();
        } catch (Exception ex) { throw new AssertionError("MIME sintético inválido", ex); }
    }

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withUserConfiguration(EnvioCodigoAccesoCorreoAdapter.class,
                        EnvioBienvenidaCorreoAdapter.class, AvisoTicketCriticoCorreoAdapter.class,
                        DespacharCodigosService.class)
                .withBean(CorreoTransportePort.class, () -> mock(CorreoTransportePort.class))
                .withBean(CifradoGmailPort.class, () -> mock(CifradoGmailPort.class))
                .withBean(CorreoCodigoStore.class, () -> mock(CorreoCodigoStore.class))
                .withBean(CodigoAccesoRepository.class, () -> mock(CodigoAccesoRepository.class))
                .withBean(IdentidadFachada.class, () -> mock(IdentidadFachada.class));
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void tresControlesSonIndependientes(boolean auth, boolean bienvenida, boolean critico) {
        contexto().withPropertyValues("dimabug.auth.correo.habilitado=" + auth,
                "dimabug.correo.bienvenida.habilitado=" + bienvenida,
                "dimabug.correo.ticket-critico.habilitado=" + critico).run(ctx -> {
            assertNull(ctx.getStartupFailure());
            var transporte = ctx.getBean(CorreoTransportePort.class);
            var cifrado = ctx.getBean(CifradoGmailPort.class);
            var store = ctx.getBean(CorreoCodigoStore.class);
            var identidad = ctx.getBean(IdentidadFachada.class);
            when(transporte.disponible()).thenReturn(true);
            when(transporte.remitente()).thenReturn("remitente@example.invalid");
            when(transporte.enviarAutenticacion(anyString())).thenReturn("GMAIL-test");
            when(cifrado.disponible()).thenReturn(true);
            var rawAuth = new AtomicReference<String>();
            when(cifrado.cifrar(anyString(), anyString())).thenAnswer(call -> {
                rawAuth.set(call.getArgument(0)); return "cifrado-sintetico";
            });
            when(cifrado.descifrar(eq("cifrado-sintetico"), anyString())).thenAnswer(call -> rawAuth.get());
            when(identidad.buscarPorId(anyInt())).thenReturn(Optional.empty());
            when(identidad.listarActivos()).thenReturn(List.of(
                    new UsuarioPublico(2, "Prueba", "tecnico@example.invalid", "TECNICO", true)));
            var ticket = mock(Ticket.class);
            when(ticket.getId()).thenReturn(1L);
            when(ticket.getCodigo()).thenReturn("TK-QA");
            when(ticket.getTitulo()).thenReturn("Prueba sintética");
            when(ticket.getSolicitanteId()).thenReturn(1);
            var ahora = LocalDateTime.now();
            var codigo = new CodigoAcceso(1L, 1, "hash-sintetico", ahora, ahora.plusMinutes(5), 0, false);

            TransactionSynchronizationManager.initSynchronization();
            try {
                ctx.getBean(EnvioCodigoAccesoCorreoAdapter.class)
                        .encolar(codigo, "tecnico@example.invalid", "Prueba", "000123", 5);
                ctx.getBean(EnvioBienvenidaCorreoAdapter.class)
                        .enviar("tecnico@example.invalid", "Prueba", "TECNICO");
                ctx.getBean(AvisoTicketCriticoCorreoAdapter.class).avisar(ticket, 1);
                var callbacks = TransactionSynchronizationManager.getSynchronizations();
                assertEquals((bienvenida ? 1 : 0) + (critico ? 1 : 0), callbacks.size(),
                        "Los emisores deshabilitados no registran trabajos post-commit");
                verify(transporte, never()).enviarAutenticacion(anyString());
                if (!auth) { verifyNoInteractions(store, cifrado); }
                if (!critico) { verifyNoInteractions(identidad, ticket); }
                for (var callback : callbacks) callback.afterCommit();
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }

            assertEquals(auth ? 1 : 0, ctx.getBeansOfType(DespacharCodigosService.class).size());
            if (auth) {
                var id = ArgumentCaptor.forClass(String.class);
                verify(store).guardar(id.capture(), eq(1L), eq("cifrado-sintetico"),
                        eq(codigo.expiraEn()), eq(CorreoCodigoStore.Estado.PENDIENTE));
                String trabajoId = id.getValue();
                when(store.reservar(eq(trabajoId), any())).thenReturn(Optional.of(
                        new CorreoCodigoStore.Trabajo(trabajoId, 1L, "cifrado-sintetico", codigo.expiraEn(), 1)));
                when(ctx.getBean(CodigoAccesoRepository.class).vigente(eq(1L), any())).thenReturn(true);
                ctx.getBean(DespacharCodigosService.class).procesar(trabajoId);
                verify(store).terminar(eq(trabajoId), eq(CorreoCodigoStore.Estado.ENVIADO), eq("GMAIL-test"), any());
            }
            int esperados = (auth ? 1 : 0) + (bienvenida ? 1 : 0) + (critico ? 1 : 0);
            if (esperados == 0) verify(transporte, never()).enviarAutenticacion(anyString());
            else {
                var mensajes = ArgumentCaptor.forClass(String.class);
                verify(transporte, timeout(3000).times(esperados)).enviarAutenticacion(mensajes.capture());
                var asuntos = new ArrayList<String>();
                for (String raw : mensajes.getAllValues()) {
                    asuntos.add(asunto(raw));
                }
                assertEquals(auth ? 1 : 0, asuntos.stream().filter(s -> s.startsWith("Tu código de acceso")).count());
                assertEquals(bienvenida ? 1 : 0, asuntos.stream().filter(s -> s.startsWith("Tu cuenta en DimaBug")).count());
                assertEquals(critico ? 1 : 0, asuntos.stream().filter(s -> s.startsWith("Ticket crítico")).count());
            }
            verify(transporte, never()).enviar(anyString());
        });
    }

    @Test void bienvenidaYTicketsDeshabilitadosPorDefectoSinTrabajosNiDependenciasUsadas() {
        contexto().withInitializer(c -> {
            c.getEnvironment().getPropertySources().remove("systemEnvironment");
            c.getEnvironment().getPropertySources().remove("systemProperties");
        }).withPropertyValues("dimabug.auth.correo.habilitado=false").run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertFalse(ctx.getEnvironment().containsProperty("dimabug.correo.bienvenida.habilitado"));
            assertFalse(ctx.getEnvironment().containsProperty("dimabug.correo.ticket-critico.habilitado"));
            TransactionSynchronizationManager.initSynchronization();
            try {
                ctx.getBean(EnvioBienvenidaCorreoAdapter.class).enviar("qa@example.invalid", "Prueba", "TECNICO");
                ctx.getBean(AvisoTicketCriticoCorreoAdapter.class).avisar(null, null);
                ctx.getBean(EnvioCodigoAccesoCorreoAdapter.class).encolar(null, null, null, null, 5);
                assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
            verifyNoInteractions(ctx.getBean(CorreoTransportePort.class), ctx.getBean(CorreoCodigoStore.class),
                    ctx.getBean(CifradoGmailPort.class), ctx.getBean(IdentidadFachada.class));
        });
    }
    @Test void variablesDeStagingHabilitanSoloAutenticacion() {
        contexto().withInitializer(c -> {
            c.getEnvironment().getPropertySources().remove("systemEnvironment");
            c.getEnvironment().getPropertySources().remove("systemProperties");
            c.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "staging-sintetico", Map.of("DIMABUG_AUTH_CORREO_HABILITADO", "true",
                    "DIMABUG_CORREO_BIENVENIDA_HABILITADO", "false",
                    "DIMABUG_CORREO_TICKET_CRITICO_HABILITADO", "false")));
        }).run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertEquals(true, org.springframework.test.util.ReflectionTestUtils.getField(ctx.getBean(EnvioCodigoAccesoCorreoAdapter.class), "habilitado"));
            assertEquals(false, org.springframework.test.util.ReflectionTestUtils.getField(ctx.getBean(EnvioBienvenidaCorreoAdapter.class), "habilitado"));
            assertEquals(false, org.springframework.test.util.ReflectionTestUtils.getField(ctx.getBean(AvisoTicketCriticoCorreoAdapter.class), "habilitado"));
            assertEquals(1, ctx.getBeansOfType(DespacharCodigosService.class).size());
            ctx.getBean(EnvioBienvenidaCorreoAdapter.class).enviar(null, null, null);
            ctx.getBean(AvisoTicketCriticoCorreoAdapter.class).avisar(null, null);
            verifyNoInteractions(ctx.getBean(CorreoTransportePort.class), ctx.getBean(CorreoCodigoStore.class), ctx.getBean(IdentidadFachada.class));
        });
    }

}
