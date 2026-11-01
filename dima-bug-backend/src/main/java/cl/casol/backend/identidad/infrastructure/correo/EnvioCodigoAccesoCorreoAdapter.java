package cl.casol.backend.identidad.infrastructure.correo;

import cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort;
import cl.casol.backend.identidad.domain.exception.EnvioCodigoAccesoException;
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

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Envía el código de acceso por correo (Gmail u otro SMTP configurado en spring.mail.*). */
@Component
public class EnvioCodigoAccesoCorreoAdapter implements EnvioCodigoAccesoPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EnvioCodigoAccesoCorreoAdapter.class);

    /** Imagen de cabecera del correo, incrustada en el mensaje (no depende de internet). */
    private static final String BANNER = "correo/banner-codigo-acceso.jpg";
    private static final String BANNER_CID = "bannerDimaBug";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;
    private final String nombreRemitente;
    private final boolean mostrarEnLog;

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
            @Value("${spring.mail.username:}") String remitente,
            @Value("${dimabug.auth.codigo.remitente-nombre:DimaBug}") String nombreRemitente,
            @Value("${dimabug.auth.codigo.mostrar-en-log:false}") boolean mostrarEnLog
    ) {
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
        this.mostrarEnLog = mostrarEnLog;
    }

    @Override
    public void enviar(String email, String nombre, String codigo, int minutosVigencia) {
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

    private void enviarAhora(JavaMailSender sender, String email, String nombre, String codigo, int minutosVigencia) {
        try {
            ClassPathResource banner = new ClassPathResource(BANNER);
            boolean conBanner = banner.exists();

            MimeMessage mensaje = sender.createMimeMessage();
            // true = multipart: lleva versión en texto plano, versión HTML y la imagen de cabecera.
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente, nombreRemitente);
            helper.setTo(email);
            // Asunto distinto en cada correo: así Gmail no los agrupa en un mismo hilo
            // ni oculta el contenido repetido (que es lo que hacía "desaparecer" el banner).
            helper.setSubject("Tu código de acceso a DimaBug: " + codigo);
            helper.setSentDate(new Date());
            helper.setText(texto(nombre, codigo, minutosVigencia), html(nombre, codigo, minutosVigencia, conBanner));
            if (conBanner) {
                helper.addInline(BANNER_CID, banner, "image/jpeg");
            }
            // Identificador único por correo: evita que Gmail recorte partes "ya vistas" del mensaje.
            mensaje.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString());
            // Marca el correo como automático y transaccional (no masivo).
            mensaje.setHeader("Auto-Submitted", "auto-generated");
            sender.send(mensaje);
            log.info("Código de acceso enviado a {}", email);
        } catch (Exception e) {
            log.error("No se pudo enviar el código de acceso a {}", email, e);
        }
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
        String cabecera = conBanner
                ? """
                  <tr><td align="center" bgcolor="#1a1a1a" style="padding:0;line-height:0;font-size:0;background-color:#1a1a1a;">
                    <img src="cid:%s" width="600" alt="DimaBug" style="display:block;width:100%%;max-width:600px;height:auto;border:0;background-color:#1a1a1a;color:#ffffff;font-family:Arial,Helvetica,sans-serif;font-size:24px;font-weight:bold;line-height:180px;text-align:center;">
                  </td></tr>
                  """.formatted(BANNER_CID)
                : """
                  <tr><td align="center" bgcolor="#1a1a1a" style="padding:28px 36px;background-color:#1a1a1a;border-bottom:4px solid #1e4fd6;">
                    <span style="font-size:24px;font-weight:bold;color:#ffffff;">DimaBug</span>
                  </td></tr>
                  """;
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Código de acceso DimaBug</title></head>
                <body style="margin:0;padding:0;background-color:#e9eef7;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#e9eef7;">
                  <tr><td align="center" style="padding:32px 12px;">
                    <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%%;max-width:600px;border-collapse:separate;border-radius:14px;overflow:hidden;background-color:#0b1b4d;font-family:Arial,Helvetica,sans-serif;">
                      %s
                      <tr><td style="padding:32px 36px 8px 36px;color:#e2e8f0;">
                        <p style="margin:0 0 12px 0;font-size:20px;font-weight:bold;color:#ffffff;">Hola, %s</p>
                        <p style="margin:0;font-size:15px;line-height:1.6;color:#c7d2fe;">Se pidió ingresar a tu cuenta de <strong style="color:#ffffff;">DimaBug</strong>. Escribe este código en la pantalla de inicio de sesión para continuar:</p>
                      </td></tr>
                      <tr><td style="padding:24px 36px 0 36px;">
                        <p style="margin:0 0 10px 0;font-size:11px;font-weight:bold;letter-spacing:2px;color:#93c5fd;">TU CÓDIGO DE ACCESO</p>
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:separate;">
                          <tr><td align="center" style="background-color:#132a6b;border:1px solid #3b82f6;border-radius:12px;padding:22px 12px;">
                            <span style="font-family:'Courier New',Courier,monospace;font-size:38px;font-weight:bold;letter-spacing:12px;color:#ffffff;">%s</span>
                          </td></tr>
                        </table>
                        <p style="margin:16px 0 0 0;font-size:14px;line-height:1.5;color:#c7d2fe;text-align:center;">Vence en <strong style="color:#ffffff;">%d minutos</strong> y sirve para un solo ingreso. No lo compartas con nadie.</p>
                      </td></tr>
                      <tr><td style="padding:24px 36px 32px 36px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:separate;">
                          <tr><td style="background-color:#0f2260;border-left:4px solid #3b82f6;border-radius:8px;padding:14px 16px;font-size:13px;line-height:1.5;color:#a5b4fc;">
                            ¿No fuiste tú? Puedes ignorar este correo. Sin este código nadie puede entrar a tu cuenta.
                          </td></tr>
                        </table>
                      </td></tr>
                      <tr><td align="center" style="background-color:#081440;padding:18px 24px;border-top:1px solid #1e3a8a;">
                        <p style="margin:0 0 4px 0;font-size:12px;color:#7c8db5;">Mensaje automático · Por favor no respondas este correo</p>
                        <p style="margin:0;font-size:12px;font-weight:bold;color:#c7d2fe;">DimaBug · Soporte TI Holding Dimarsa</p>
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
