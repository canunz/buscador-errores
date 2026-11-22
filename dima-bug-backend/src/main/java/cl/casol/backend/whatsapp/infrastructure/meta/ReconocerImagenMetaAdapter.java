package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.application.port.out.ReconocerImagenPort;
import cl.casol.backend.whatsapp.domain.RespuestaCanal.Candidato;
import cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter;
import java.util.List;

/** Fixtures sintéticos → OCR local; identificadores reales de Meta → descarga Graph + OCR por stdin. */
public final class ReconocerImagenMetaAdapter implements ReconocerImagenPort {
    private final OcrNodeProcesoAdapter ocr;
    private final GraphMetaCliente cliente;
    private final PoliticaMetaQa politica;
    public ReconocerImagenMetaAdapter(OcrNodeProcesoAdapter ocr, GraphMetaCliente cliente, PoliticaMetaQa politica) {
        this.ocr = ocr; this.cliente = cliente; this.politica = politica;
    }
    @Override public List<Candidato> reconocer(String referenciaMedio) {
        if (referenciaMedio != null && referenciaMedio.startsWith("qa-media-")) return ocr.reconocer(referenciaMedio);
        byte[] imagen;
        try { imagen = cliente.descargarImagen(referenciaMedio, politica.numero()); }
        catch (RuntimeException e) { throw new FalloOcr(); }
        return ocr.reconocerBytes(imagen);
    }
}
