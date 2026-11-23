package cl.casol.backend.whatsapp.application.port.out;

/** Despacho real al proveedor del canal. Devuelve el identificador asignado por el proveedor. */
public interface EnviarMensajeCanalPort {
    String enviar(String destinatario, String texto);
    /** reintentable=false para errores definitivos (destinatario no autorizado, token inválido). */
    class FalloEnvio extends RuntimeException {
        private final boolean reintentable;
        public FalloEnvio(boolean reintentable) { super("No fue posible enviar el mensaje."); this.reintentable = reintentable; }
        public boolean reintentable() { return reintentable; }
    }
}
