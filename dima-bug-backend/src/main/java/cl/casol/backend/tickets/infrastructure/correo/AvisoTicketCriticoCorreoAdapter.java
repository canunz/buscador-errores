package cl.casol.backend.tickets.infrastructure.correo;

import cl.casol.backend.identidad.api.IdentidadFachada;
import cl.casol.backend.identidad.api.UsuarioPublico;
import cl.casol.backend.tickets.application.port.out.AvisoTicketCriticoPort;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.infrastructure.integracion.UsuarioConsultaAdapter;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Envía un correo a soporte y administración cuando un ticket queda con prioridad CRÍTICA,
 * además del aviso que ya aparece en el panel de notificaciones.
 */
@Component
public class AvisoTicketCriticoCorreoAdapter implements AvisoTicketCriticoPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AvisoTicketCriticoCorreoAdapter.class);

    private static final String BANNER = "correo/banner-codigo-acceso.jpg";
    private static final String BANNER_CID = "bannerDimaBug";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final IdentidadFachada identidad;
    private final String remitente;
    private final String nombreRemitente;
    private final String urlAplicacion;

    /** El envío va en segundo plano para que crear o cambiar el ticket no espere a Gmail. */
    private final ExecutorService envios = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "aviso-ticket-critico");
        t.setDaemon(true);
        return t;
    });

    public AvisoTicketCriticoCorreoAdapter(
            ObjectProvider<JavaMailSender> mailSender,
            IdentidadFachada identidad,
            @Value("${spring.mail.username:}") String remitente,
            @Value("${dimabug.auth.codigo.remitente-nombre:DimaBug}") String nombreRemitente,
            @Value("${dimabug.app.url:http://localhost:4200}") String urlAplicacion
    ) {
        this.mailSender = mailSender;
        this.identidad = identidad;
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
        this.urlAplicacion = urlAplicacion == null ? "" : urlAplicacion.trim().replaceAll("/+$", "");
    }

    @Override
    public void avisar(Ticket ticket, Integer actorId) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remitente.isEmpty()) {
            log.warn("Correo no configurado: no se envió el aviso del ticket crítico {}", ticket.getCodigo());
            return;
        }
        Long id = ticket.getId();
        String codigo = ticket.getCodigo();
        String titulo = ticket.getTitulo();
        Integer solicitanteId = ticket.getSolicitanteId();
        Runnable envio = () -> enviarATodos(sender, id, codigo, titulo, solicitanteId, actorId);

        // Se envía solo cuando el ticket ya quedó guardado en la base de datos.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envios.execute(envio);
                }
            });
        } else {
            envios.execute(envio);
        }
    }

    private void enviarATodos(JavaMailSender sender, Long id, String codigo, String titulo,
                              Integer solicitanteId, Integer actorId) {
        try {
            String solicitante = identidad.buscarPorId(solicitanteId)
                    .map(UsuarioPublico::nombre).filter(n -> n != null && !n.isBlank()).orElse("un usuario");
            List<UsuarioPublico> destinatarios = identidad.listarActivos().stream()
                    .filter(u -> UsuarioConsultaAdapter.rol(u.rol()).esStaff())
                    .filter(u -> u.email() != null && !u.email().isBlank())
                    .filter(u -> !Objects.equals(u.id(), actorId))
                    .toList();
            if (destinatarios.isEmpty()) {
                log.warn("No hay usuarios de soporte activos para avisar del ticket crítico {}", codigo);
                return;
            }
            for (UsuarioPublico destino : destinatarios) {
                enviarA(sender, destino, id, codigo, titulo, solicitante);
            }
        } catch (Exception e) {
            log.error("No se pudo preparar el aviso del ticket crítico {}", codigo, e);
        }
    }

    private void enviarA(JavaMailSender sender, UsuarioPublico destino, Long id, String codigo,
                         String titulo, String solicitante) {
        try {
            ClassPathResource banner = new ClassPathResource(BANNER);
            boolean conBanner = banner.exists();
            String enlace = urlAplicacion + "/tickets/" + id;

            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente, nombreRemitente);
            helper.setTo(destino.email());
            helper.setSubject("Ticket crítico " + codigo + ": " + titulo);
            helper.setSentDate(new Date());
            helper.setText(texto(destino.nombre(), codigo, titulo, solicitante, enlace),
                    html(destino.nombre(), codigo, titulo, solicitante, enlace, conBanner));
            if (conBanner) {
                helper.addInline(BANNER_CID, banner, "image/jpeg");
            }
            mensaje.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString());
            mensaje.setHeader("Auto-Submitted", "auto-generated");
            sender.send(mensaje);
            log.info("Aviso de ticket crítico {} enviado a {}", codigo, destino.email());
        } catch (Exception e) {
            log.error("No se pudo enviar el aviso del ticket crítico {} a {}", codigo, destino.email(), e);
        }
    }

    @Override
    public void destroy() {
        envios.shutdown();
    }

    private String texto(String nombre, String codigo, String titulo, String solicitante, String enlace) {
        return "Hola, " + saludo(nombre) + "\n\n"
                + "Se registró un ticket con prioridad CRÍTICA que necesita atención lo antes posible.\n\n"
                + "Ticket: " + codigo + "\n"
                + "Asunto: " + titulo + "\n"
                + "Reportado por: " + solicitante + "\n\n"
                + "Revísalo aquí: " + enlace + "\n\n"
                + "DimaBug · Soporte TI Holding Dimarsa\n"
                + "Mensaje automático, por favor no respondas este correo.";
    }

    private String html(String nombre, String codigo, String titulo, String solicitante, String enlace,
                        boolean conBanner) {
        String cabecera = conBanner
                ? """
                  <tr><td align="center" valign="middle" height="180" bgcolor="#ffffff" style="padding:0;height:180px;background-color:#ffffff;">
                    <img src="cid:%s" width="480" height="180" alt="DimaBug" style="display:block;width:100%%;max-width:480px;height:auto;border:0;outline:none;text-decoration:none;color:#1e4fd6;font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:bold;">
                  </td></tr>
                  """.formatted(BANNER_CID)
                : """
                  <tr><td align="center" valign="middle" height="180" bgcolor="#1e4fd6" style="height:180px;background-color:#1e4fd6;">
                    <span style="font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:bold;color:#ffffff;">DimaBug</span>
                  </td></tr>
                  """;
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Ticket crítico DimaBug</title></head>
                <body bgcolor="#eef3fb" style="margin:0;padding:0;background-color:#eef3fb;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" bgcolor="#eef3fb" style="background-color:#eef3fb;">
                  <tr><td align="center" style="padding:24px 12px;">
                    <table role="presentation" width="480" cellpadding="0" cellspacing="0" border="0" bgcolor="#ffffff" style="width:100%%;max-width:480px;background-color:#ffffff;border:1px solid #dbe4f3;border-radius:10px;overflow:hidden;font-family:Arial,Helvetica,sans-serif;">
                      %s
                      <tr><td style="padding:26px 28px 0 28px;">
                        <p style="margin:0 0 8px 0;font-size:15px;font-weight:bold;color:#0b1b4d;">Hola, %s</p>
                        <p style="margin:0;font-size:13px;line-height:1.6;color:#475569;">Se registró un ticket con prioridad crítica que necesita atención lo antes posible.</p>
                      </td></tr>
                      <tr><td style="padding:22px 28px 0 28px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                          <tr><td bgcolor="#fef2f2" style="background-color:#fef2f2;border:1px solid #ef4444;border-radius:6px;padding:18px 18px;">
                            <p style="margin:0 0 10px 0;"><span style="display:inline-block;background-color:#dc2626;color:#ffffff;font-size:10px;font-weight:bold;letter-spacing:1px;padding:4px 8px;border-radius:4px;">PRIORIDAD CRÍTICA</span></p>
                            <p style="margin:0 0 6px 0;font-size:18px;font-weight:bold;color:#0b1b4d;">%s</p>
                            <p style="margin:0 0 10px 0;font-size:13px;line-height:1.5;color:#334155;">%s</p>
                            <p style="margin:0;font-size:12px;color:#64748b;">Reportado por <span style="color:#0b1b4d;font-weight:bold;">%s</span></p>
                          </td></tr>
                        </table>
                      </td></tr>
                      <tr><td align="center" style="padding:24px 28px 26px 28px;">
                        <a href="%s" target="_blank" style="display:inline-block;background-color:#1e4fd6;color:#ffffff;font-size:13px;font-weight:bold;text-decoration:none;padding:12px 28px;border-radius:6px;">Ver ticket</a>
                      </td></tr>
                      <tr><td align="center" style="padding:16px 24px 18px 24px;border-top:1px solid #e2e8f0;">
                        <p style="margin:0 0 6px 0;font-size:10px;color:#94a3b8;">Mensaje automático · Por favor no respondas este correo</p>
                        <p style="margin:0;font-size:10px;color:#475569;">DimaBug · Soporte TI Holding Dimarsa</p>
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(cabecera,
                HtmlUtils.htmlEscape(saludo(nombre)),
                HtmlUtils.htmlEscape(codigo),
                HtmlUtils.htmlEscape(titulo == null ? "" : titulo),
                HtmlUtils.htmlEscape(solicitante),
                HtmlUtils.htmlEscape(enlace));
    }

    private String saludo(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        return limpio.isEmpty() ? "equipo de soporte" : limpio.split("\\s+")[0];
    }
}
