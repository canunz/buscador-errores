package cl.casol.backend.identidad.infrastructure.correo;

import cl.casol.backend.correo.application.service.GmailOAuthService;
import cl.casol.backend.correo.infrastructure.gmail.GmailConfig;
import cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort;
import cl.casol.backend.identidad.domain.exception.EnvioCodigoAccesoException;
import jakarta.mail.Session;
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
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Envía el código de acceso por Gmail API (HTTPS) o, si no está conectada, por SMTP. */
@Component
public class EnvioCodigoAccesoCorreoAdapter implements EnvioCodigoAccesoPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EnvioCodigoAccesoCorreoAdapter.class);

    /** Imagen de cabecera del correo, incrustada en el mensaje (no depende de internet). */
    private static final String BANNER = "correo/banner-codigo-acceso.jpg";
    private static final String BANNER_CID = "bannerDimaBug";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final GmailOAuthService gmail;
    private final GmailConfig gmailConfig;
    private final String remitente;
    private final String nombreRemitente;
    private final boolean mostrarEnLog;
    private final boolean exigirGmailApi;

    /**
     * Gmail tarda varios segundos en aceptar un correo. El envío se hace en segundo plano para que
     * la pantalla avance de inmediato; si falla, queda en el log y el usuario puede reenviar.
     */
    private final ExecutorService envios = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "envio-codigo-acceso");
        t.setDaemon(true);
        return t;
    });

    public EnvioCodigoAccesoCorreoAdapter(
            ObjectProvider<JavaMailSender> mailSender,
            GmailOAuthService gmail,
            GmailConfig gmailConfig,
            @Value("${spring.mail.username:}") String remitente,
            @Value("${dimabug.auth.codigo.remitente-nombre:DimaBug}") String nombreRemitente,
            @Value("${dimabug.auth.codigo.mostrar-en-log:false}") boolean mostrarEnLog,
            @Value("${dimabug.auth.codigo.usar-gmail-api:false}") boolean exigirGmailApi
    ) {
        this.mailSender = mailSender;
        this.gmail = gmail;
        this.gmailConfig = gmailConfig;
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
        this.mostrarEnLog = mostrarEnLog;
        this.exigirGmailApi = exigirGmailApi;
    }

    @Override
    public void enviar(String email, String nombre, String codigo, int minutosVigencia) {
        if (gmail.disponible()) {
            envios.execute(() -> enviarPorGmailApi(email, nombre, codigo, minutosVigencia));
            return;
        }
        if (exigirGmailApi) {
            throw new EnvioCodigoAccesoException(new IllegalStateException(
                    "Gmail API no está conectada. Un administrador debe autorizar la cuenta en Administración > Gmail."));
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remitente.isEmpty()) {
            if (mostrarEnLog) {
                // Solo para desarrollo local, cuando aún no se configura el correo.
                log.warn("Correo no configurado. Código de acceso para {}: {}", email, codigo);
                return;
            }
            throw new EnvioCodigoAccesoException(new IllegalStateException("spring.mail no configurado"));
        }
        envios.execute(() -> enviarAhora(sender, email, nombre, codigo, minutosVigencia));
    }

    private void enviarPorGmailApi(String email, String nombre, String codigo, int minutosVigencia) {
        try {
            String from = gmailConfig.remitente() == null || gmailConfig.remitente().isBlank()
                    ? remitente
                    : gmailConfig.remitente().strip();
            MimeMessage mensaje = new MimeMessage(Session.getInstance(new Properties()));
            completarMensaje(mensaje, from, email, nombre, codigo, minutosVigencia);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            mensaje.writeTo(out);
            gmail.enviar(Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray()));
            log.info("Código de acceso enviado a {} vía Gmail API", email);
        } catch (Exception e) {
            log.error("No se pudo enviar el código de acceso a {}", email, e);
        }
    }

    private void enviarAhora(JavaMailSender sender, String email, String nombre, String codigo, int minutosVigencia) {
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            completarMensaje(mensaje, remitente, email, nombre, codigo, minutosVigencia);
            sender.send(mensaje);
            log.info("Código de acceso enviado a {}", email);
        } catch (Exception e) {
            log.error("No se pudo enviar el código de acceso a {}", email, e);
        }
    }

    private void completarMensaje(MimeMessage mensaje, String from, String email, String nombre, String codigo, int minutosVigencia) throws Exception {
        ClassPathResource banner = new ClassPathResource(BANNER);
        boolean conBanner = banner.exists();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
        helper.setFrom(from, nombreRemitente);
        helper.setTo(email);
        helper.setSubject("Tu código de acceso a DimaBug: " + codigo);
        helper.setSentDate(new Date());
        helper.setText(texto(nombre, codigo, minutosVigencia), html(nombre, codigo, minutosVigencia, conBanner));
        if (conBanner) {
            helper.addInline(BANNER_CID, banner, "image/jpeg");
        }
        mensaje.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString());
        mensaje.setHeader("Auto-Submitted", "auto-generated");
    }

    @Override
    public void destroy() {
        envios.shutdown();
    }

    private String texto(String nombre, String codigo, int minutos) {
        return "Hola, " + saludo(nombre) + "\n\n"
                + "Se pidió ingresar a tu cuenta de DimaBug. "
                + "Escribe este código en la pantalla de inicio de sesión para continuar:\n\n"
                + "    " + codigo + "\n\n"
                + "Vence en " + minutos + " minutos y sirve para un solo ingreso. No lo compartas con nadie.\n\n"
                + "¿No fuiste tú? Puedes ignorar este correo. Sin este código nadie puede entrar a tu cuenta.\n\n"
                + "DimaBug · Soporte TI Holding Dimarsa\n"
                + "Mensaje automático, por favor no respondas este correo.";
    }

    private String html(String nombre, String codigo, int minutos, boolean conBanner) {
        // Si el lector bloquea imágenes, la franja mantiene su alto y color y muestra "DimaBug".
        String cabecera = conBanner
                ? """
                  <tr><td align="center" valign="middle" height="144" bgcolor="#1a1a1a" style="padding:0;height:144px;background-color:#1a1a1a;">
                    <img src="cid:%s" width="480" height="144" alt="DimaBug" style="display:block;width:100%%;max-width:480px;height:auto;border:0;outline:none;text-decoration:none;color:#ffffff;font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:bold;">
                  </td></tr>
                  """.formatted(BANNER_CID)
                : """
                  <tr><td align="center" valign="middle" height="144" bgcolor="#1a1a1a" style="height:144px;background-color:#1a1a1a;border-bottom:3px solid #1e4fd6;">
                    <span style="font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:bold;color:#ffffff;">DimaBug</span>
                  </td></tr>
                  """;
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Código de acceso DimaBug</title></head>
                <body bgcolor="#2a2a2a" style="margin:0;padding:0;background-color:#2a2a2a;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" bgcolor="#2a2a2a" style="background-color:#2a2a2a;">
                  <tr><td align="center" style="padding:24px 12px;">
                    <table role="presentation" width="480" cellpadding="0" cellspacing="0" border="0" bgcolor="#1a1a1a" style="width:100%%;max-width:480px;background-color:#1a1a1a;font-family:Arial,Helvetica,sans-serif;">
                      %s
                      <tr><td style="padding:26px 28px 0 28px;">
                        <p style="margin:0 0 8px 0;font-size:15px;font-weight:bold;color:#f1f5f9;">Hola, %s</p>
                        <p style="margin:0;font-size:13px;line-height:1.6;color:#b4b4b4;">Se pidió ingresar a tu cuenta de DimaBug. Escribe este código en la pantalla de inicio de sesión para continuar:</p>
                      </td></tr>
                      <tr><td style="padding:22px 28px 0 28px;">
                        <p style="margin:0 0 10px 0;font-size:10px;letter-spacing:1px;color:#8a8a8a;">TU CÓDIGO DE ACCESO</p>
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                          <tr><td align="center" bgcolor="#141414" style="background-color:#141414;border:1px solid #3b82f6;border-radius:6px;padding:20px 12px;">
                            <span style="font-family:Arial,Helvetica,sans-serif;font-size:28px;font-weight:bold;letter-spacing:8px;color:#dbeafe;">%s</span>
                          </td></tr>
                        </table>
                        <p style="margin:16px 0 0 0;font-size:12px;line-height:1.5;color:#b4b4b4;text-align:center;">Vence en <strong style="color:#ffffff;">%d minutos</strong> y sirve para un solo ingreso. No lo compartas con nadie.</p>
                      </td></tr>
                      <tr><td style="padding:22px 28px 26px 28px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                          <tr><td bgcolor="#222222" style="background-color:#222222;border-left:3px solid #4a4a4a;border-radius:4px;padding:12px 14px;font-size:11px;line-height:1.6;color:#9a9a9a;">
                            ¿No fuiste tú? Puedes ignorar este correo. Sin este código nadie puede entrar a tu cuenta.
                          </td></tr>
                        </table>
                      </td></tr>
                      <tr><td align="center" style="padding:16px 24px 18px 24px;border-top:1px solid #2e2e2e;">
                        <p style="margin:0 0 6px 0;font-size:10px;color:#7a7a7a;">Mensaje automático · Por favor no respondas este correo</p>
                        <p style="margin:0;font-size:10px;color:#b4b4b4;">DimaBug · Soporte TI Holding Dimarsa</p>
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(cabecera, HtmlUtils.htmlEscape(saludo(nombre)), HtmlUtils.htmlEscape(codigo), minutos);
    }

    /** Usa solo el primer nombre para que el saludo sea más cercano. */
    private String saludo(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return "usuario";
        }
        return limpio.split("\\s+")[0];
    }
}
