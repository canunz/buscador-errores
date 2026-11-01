package cl.casol.backend.identidad.infrastructure.correo;

import cl.casol.backend.identidad.application.port.out.BienvenidaUsuarioPort;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Envía el correo de bienvenida cuando el administrador crea una cuenta nueva. */
@Component
public class EnvioBienvenidaCorreoAdapter implements BienvenidaUsuarioPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EnvioBienvenidaCorreoAdapter.class);

    private static final String BANNER = "correo/banner-codigo-acceso.jpg";
    private static final String BANNER_CID = "bannerDimaBug";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;
    private final String nombreRemitente;
    private final String urlAplicacion;

    /** El envío va en segundo plano para que crear el usuario no espere a Gmail. */
    private final ExecutorService envios = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "correo-bienvenida");
        t.setDaemon(true);
        return t;
    });

    public EnvioBienvenidaCorreoAdapter(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.username:}") String remitente,
            @Value("${dimabug.auth.codigo.remitente-nombre:DimaBug}") String nombreRemitente,
            @Value("${dimabug.app.url:http://localhost:4200}") String urlAplicacion
    ) {
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
        this.urlAplicacion = urlAplicacion == null ? "" : urlAplicacion.trim().replaceAll("/+$", "");
    }

    @Override
    public void enviar(String email, String nombre, String rol) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remitente.isEmpty()) {
            log.warn("Correo no configurado: no se envió la bienvenida a {}", email);
            return;
        }
        Runnable envio = () -> enviarAhora(sender, email, nombre, rol);
        // Se envía solo cuando la cuenta ya quedó guardada en la base de datos.
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

    private void enviarAhora(JavaMailSender sender, String email, String nombre, String rol) {
        try {
            ClassPathResource banner = new ClassPathResource(BANNER);
            boolean conBanner = banner.exists();
            String enlace = urlAplicacion + "/login";

            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente, nombreRemitente);
            helper.setTo(email);
            helper.setSubject("Tu cuenta en DimaBug ya está lista");
            helper.setSentDate(new Date());
            helper.setText(texto(nombre, email, rol, enlace), html(nombre, email, rol, enlace, conBanner));
            if (conBanner) {
                helper.addInline(BANNER_CID, banner, "image/jpeg");
            }
            mensaje.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString());
            mensaje.setHeader("Auto-Submitted", "auto-generated");
            sender.send(mensaje);
            log.info("Correo de bienvenida enviado a {}", email);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo de bienvenida a {}", email, e);
        }
    }

    @Override
    public void destroy() {
        envios.shutdown();
    }

    private String texto(String nombre, String email, String rol, String enlace) {
        return "Hola, " + saludo(nombre) + "\n\n"
                + "Se acaba de crear tu cuenta en DimaBug, la plataforma de Soporte TI de Holding Dimarsa.\n\n"
                + "Correo de acceso: " + email + "\n"
                + (rol == null || rol.isBlank() ? "" : "Perfil: " + rol + "\n")
                + "\nNo necesitas contraseña: en la pantalla de inicio escribe tu correo y te enviaremos "
                + "un código de acceso cada vez que quieras ingresar.\n\n"
                + "Ingresa aquí: " + enlace + "\n\n"
                + "DimaBug · Soporte TI Holding Dimarsa\n"
                + "Mensaje automático, por favor no respondas este correo.";
    }

    private String html(String nombre, String email, String rol, String enlace, boolean conBanner) {
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
        String filaRol = rol == null || rol.isBlank() ? "" : """
                            <p style="margin:8px 0 0 0;font-size:12px;color:#64748b;">Perfil: <span style="color:#0b1b4d;font-weight:bold;">%s</span></p>
                """.formatted(HtmlUtils.htmlEscape(rol));
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Bienvenido a DimaBug</title></head>
                <body bgcolor="#eef3fb" style="margin:0;padding:0;background-color:#eef3fb;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" bgcolor="#eef3fb" style="background-color:#eef3fb;">
                  <tr><td align="center" style="padding:24px 12px;">
                    <table role="presentation" width="480" cellpadding="0" cellspacing="0" border="0" bgcolor="#ffffff" style="width:100%%;max-width:480px;background-color:#ffffff;border:1px solid #dbe4f3;border-radius:10px;overflow:hidden;font-family:Arial,Helvetica,sans-serif;">
                      %s
                      <tr><td style="padding:26px 28px 0 28px;">
                        <p style="margin:0 0 8px 0;font-size:15px;font-weight:bold;color:#0b1b4d;">Hola, %s</p>
                        <p style="margin:0;font-size:13px;line-height:1.6;color:#475569;">Se acaba de crear tu cuenta en <strong style="color:#0b1b4d;">DimaBug</strong>, la plataforma de Soporte TI de Holding Dimarsa.</p>
                      </td></tr>
                      <tr><td style="padding:22px 28px 0 28px;">
                        <p style="margin:0 0 10px 0;font-size:10px;letter-spacing:1px;font-weight:bold;color:#1e4fd6;">TU CORREO DE ACCESO</p>
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                          <tr><td bgcolor="#f1f6ff" style="background-color:#f1f6ff;border:1px solid #3b82f6;border-radius:6px;padding:16px 18px;">
                            <p style="margin:0;font-size:15px;font-weight:bold;color:#0b1b4d;">%s</p>
                            %s
                          </td></tr>
                        </table>
                        <p style="margin:16px 0 0 0;font-size:12px;line-height:1.6;color:#475569;">No necesitas contraseña: en la pantalla de inicio escribe tu correo y te enviaremos un <strong style="color:#0b1b4d;">código de acceso</strong> cada vez que quieras ingresar.</p>
                      </td></tr>
                      <tr><td align="center" style="padding:24px 28px 26px 28px;">
                        <a href="%s" target="_blank" style="display:inline-block;background-color:#1e4fd6;color:#ffffff;font-size:13px;font-weight:bold;text-decoration:none;padding:12px 28px;border-radius:6px;">Ingresar a DimaBug</a>
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
                HtmlUtils.htmlEscape(email),
                filaRol,
                HtmlUtils.htmlEscape(enlace));
    }

    private String saludo(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        return limpio.isEmpty() ? "bienvenido" : limpio.split("\\s+")[0];
    }
}
