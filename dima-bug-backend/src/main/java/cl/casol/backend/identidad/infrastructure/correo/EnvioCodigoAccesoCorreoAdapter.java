package cl.casol.backend.identidad.infrastructure.correo;

import cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

/** Construye el MIME y lo guarda cifrado en la transacción de emisión. Solo Gmail API. */
@Component
public class EnvioCodigoAccesoCorreoAdapter implements EnvioCodigoAccesoPort {
    private static final String BANNER = "correo/banner-codigo-acceso.jpg";
    private static final String BANNER_CID = "bannerDimaBug";
    private final cl.casol.backend.correo.application.port.out.CorreoTransportePort transporte;
    private final cl.casol.backend.correo.application.port.out.CifradoGmailPort cifrado;
    private final cl.casol.backend.identidad.application.port.out.CorreoCodigoStore store;
    private final String nombreRemitente;
    private final boolean habilitado;

    public EnvioCodigoAccesoCorreoAdapter(
            cl.casol.backend.correo.application.port.out.CorreoTransportePort transporte,
            cl.casol.backend.correo.application.port.out.CifradoGmailPort cifrado,
            cl.casol.backend.identidad.application.port.out.CorreoCodigoStore store,
            @Value("${dimabug.auth.codigo.remitente-nombre:DimaBug}") String nombreRemitente,
            @Value("${dimabug.auth.correo.habilitado:true}") boolean habilitado) {
        this.transporte = transporte; this.cifrado = cifrado; this.store = store;
        this.nombreRemitente = nombreRemitente;
        this.habilitado = habilitado;
    }

    // Encola el registro.
    @Override
    public void encolar(cl.casol.backend.identidad.domain.CodigoAcceso registro,
                        String email, String nombre, String codigo, int minutosVigencia) {
        if (!habilitado) return;
        String id = UUID.randomUUID().toString();
        String contenido = null;
        var estado = cl.casol.backend.identidad.application.port.out.CorreoCodigoStore.Estado.PENDIENTE;
        try {
            if (!cifrado.disponible()) throw new IllegalStateException();
            MimeMessage mensaje = new MimeMessage(Session.getInstance(new Properties()));
            completarMensaje(mensaje, transporte.remitente(), email, nombre, codigo, minutosVigencia, false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            mensaje.writeTo(out);
            contenido = cifrado.cifrar(Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray()), "auth:correo:" + id);
        } catch (Exception ex) {
            // No se propaga MIME, código, destinatario ni respuestas de proveedores a logs/API.
            estado = cl.casol.backend.identidad.application.port.out.CorreoCodigoStore.Estado.NO_CONFIGURADO;
        }
        store.guardar(id, registro.id(), contenido, registro.expiraEn(), estado);
    }

    // Envía el email.
    @Override
    public void enviar(String email, String nombre, String codigo, int minutosVigencia) {
        throw new IllegalStateException("El envío de códigos requiere un registro persistido");
    }

    // Envía recuperación.
    @Override
    public void enviarRecuperacion(String email, String nombre, String codigo, int minutosVigencia) {
        throw new IllegalStateException("La recuperación se realiza mediante inicio de sesión con código");
    }

    // Completa mensaje.
    private void completarMensaje(MimeMessage mensaje, String from, String email, String nombre, String codigo, int minutosVigencia, boolean recuperacion) throws Exception {
        ClassPathResource banner = new ClassPathResource(BANNER);
        boolean conBanner = banner.exists();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
        helper.setFrom(from, nombreRemitente);
        helper.setTo(email);
        helper.setSubject(recuperacion
                ? "Tu código de recuperación: " + codigo
                : "Tu código de acceso a DimaBug: " + codigo);
        helper.setSentDate(new Date());
        helper.setText(texto(nombre, codigo, minutosVigencia, recuperacion), html(nombre, codigo, minutosVigencia, conBanner, recuperacion));
        if (conBanner) {
            helper.addInline(BANNER_CID, banner, "image/jpeg");
        }
        mensaje.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString());
        mensaje.setHeader("Auto-Submitted", "auto-generated");
    }

    // Devuelve texto.
    private String texto(String nombre, String codigo, int minutos, boolean recuperacion) {
        if (recuperacion) {
            return "Hola, " + saludo(nombre) + "\n\n"
                    + "Se pidió recuperar la contraseña de tu cuenta de DimaBug. "
                    + "Escribe este código en la pantalla de recuperación para continuar:\n\n"
                    + "    " + codigo + "\n\n"
                    + "Vence en " + minutos + " minutos y sirve para un solo cambio de clave. No lo compartas con nadie.\n\n"
                    + "¿No fuiste tú? Puedes ignorar este correo. Sin este código nadie puede cambiar tu contraseña.\n\n"
                    + "DimaBug · Soporte TI Holding Dimarsa\n"
                    + "Mensaje automático, por favor no respondas este correo.";
        }
        return "Hola, " + saludo(nombre) + "\n\n"
                + "Se pidió ingresar a tu cuenta de DimaBug. "
                + "Escribe este código en la pantalla de inicio de sesión para continuar:\n\n"
                + "    " + codigo + "\n\n"
                + "Vence en " + minutos + " minutos y sirve para un solo ingreso. No lo compartas con nadie.\n\n"
                + "¿No fuiste tú? Puedes ignorar este correo. Sin este código nadie puede entrar a tu cuenta.\n\n"
                + "DimaBug · Soporte TI Holding Dimarsa\n"
                + "Mensaje automático, por favor no respondas este correo.";
    }

    // Devuelve html.
    private String html(String nombre, String codigo, int minutos, boolean conBanner, boolean recuperacion) {
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
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>%s</title></head>
                <body bgcolor="#2a2a2a" style="margin:0;padding:0;background-color:#2a2a2a;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" bgcolor="#2a2a2a" style="background-color:#2a2a2a;">
                  <tr><td align="center" style="padding:24px 12px;">
                    <table role="presentation" width="480" cellpadding="0" cellspacing="0" border="0" bgcolor="#1a1a1a" style="width:100%%;max-width:480px;background-color:#1a1a1a;font-family:Arial,Helvetica,sans-serif;">
                      %s
                      <tr><td style="padding:26px 28px 0 28px;">
                        <p style="margin:0 0 8px 0;font-size:15px;font-weight:bold;color:#f1f5f9;">Hola, %s</p>
                        <p style="margin:0;font-size:13px;line-height:1.6;color:#b4b4b4;">%s</p>
                      </td></tr>
                      <tr><td style="padding:22px 28px 0 28px;">
                        <p style="margin:0 0 10px 0;font-size:10px;letter-spacing:1px;color:#8a8a8a;">%s</p>
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                          <tr><td align="center" bgcolor="#141414" style="background-color:#141414;border:1px solid #3b82f6;border-radius:6px;padding:20px 12px;">
                            <span style="font-family:Arial,Helvetica,sans-serif;font-size:28px;font-weight:bold;letter-spacing:8px;color:#dbeafe;">%s</span>
                          </td></tr>
                        </table>
                        <p style="margin:16px 0 0 0;font-size:12px;line-height:1.5;color:#b4b4b4;text-align:center;">Vence en <strong style="color:#ffffff;">%d minutos</strong> y %s. No lo compartas con nadie.</p>
                      </td></tr>
                      <tr><td style="padding:22px 28px 26px 28px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                          <tr><td bgcolor="#222222" style="background-color:#222222;border-left:3px solid #4a4a4a;border-radius:4px;padding:12px 14px;font-size:11px;line-height:1.6;color:#9a9a9a;">
                            %s
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
                """.formatted(
                        recuperacion ? "Código de recuperación DimaBug" : "Código de acceso DimaBug",
                        cabecera,
                        HtmlUtils.htmlEscape(saludo(nombre)),
                        recuperacion
                                ? "Se pidió recuperar la contraseña de tu cuenta de DimaBug. Escribe este código en la pantalla de recuperación para continuar:"
                                : "Se pidió ingresar a tu cuenta de DimaBug. Escribe este código en la pantalla de inicio de sesión para continuar:",
                        recuperacion ? "TU CÓDIGO DE RECUPERACIÓN" : "TU CÓDIGO DE ACCESO",
                        HtmlUtils.htmlEscape(codigo),
                        minutos,
                        recuperacion ? "sirve para un solo cambio de clave" : "sirve para un solo ingreso",
                        recuperacion
                                ? "¿No fuiste tú? Puedes ignorar este correo. Sin este código nadie puede cambiar tu contraseña."
                                : "¿No fuiste tú? Puedes ignorar este correo. Sin este código nadie puede entrar a tu cuenta."
                );
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
