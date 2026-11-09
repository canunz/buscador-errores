package cl.casol.backend.correo.application.service;
/** Mensajes públicos constantes: nunca envolver cuerpos o excepciones de Google con secretos. */
public class GmailException extends RuntimeException {
    public enum Tipo { CONFIGURACION, ESTADO, AUTORIZACION, CONEXION, ENVIO, CONFLICTO, RECHAZO }
    public final Tipo tipo;
    public GmailException(Tipo tipo){super(switch(tipo){
        case CONFIGURACION->"La integración Gmail no está configurada o habilitada";
        case ESTADO->"La autorización OAuth expiró, ya se utilizó o no corresponde a este navegador. Inicia una nueva autorización";
        case AUTORIZACION->"Google no autorizó la operación. Revisa los permisos o vuelve a conectar la cuenta Gmail";
        case CONEXION->"No fue posible confirmar la conexión con Google. No se reintentará un envío automáticamente";
        case ENVIO->"Gmail API no confirmó el envío. La entrega puede ser incierta; revisa el historial antes de reenviar";
        case CONFLICTO->"La autorización cambió durante la operación. Actualiza su estado antes de continuar";
        case RECHAZO->"Gmail API rechazó la solicitud. El mensaje no se registró como enviado";
    });this.tipo=tipo;}
}
