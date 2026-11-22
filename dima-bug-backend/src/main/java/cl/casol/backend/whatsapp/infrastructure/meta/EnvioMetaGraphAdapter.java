package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.application.port.out.EnviarMensajeCanalPort;

/** Responde solo a participantes autorizados en la lista QA, desde el número QA configurado. */
public final class EnvioMetaGraphAdapter implements EnviarMensajeCanalPort {
    private final GraphMetaCliente cliente;
    private final PoliticaMetaQa politica;
    public EnvioMetaGraphAdapter(GraphMetaCliente cliente, PoliticaMetaQa politica) { this.cliente = cliente; this.politica = politica; }
    @Override public String enviar(String destinatario, String texto) {
        String telefono;
        try { telefono = politica.telefono(destinatario); }
        catch (PoliticaMetaQa.NoAutorizado e) { throw new FalloEnvio(false); }
        if (texto == null || texto.isBlank()) throw new FalloEnvio(false);
        return cliente.enviarTexto(politica.numero(), telefono, texto);
    }
}
