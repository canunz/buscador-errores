package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.conocimiento.domain.TipoMaterial;
import cl.casol.backend.shared.application.archivo.*;
import cl.casol.backend.shared.application.port.out.AlmacenamientoArchivoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.util.function.Function;
import static cl.casol.backend.shared.application.archivo.ArchivoException.Motivo.*;

@Service
public class ArchivoMaterialService {
    private static final Logger log = LoggerFactory.getLogger(ArchivoMaterialService.class);
    private final AlmacenamientoArchivoPort almacenamiento;
    private final TransactionTemplate transaccion;

    public ArchivoMaterialService(AlmacenamientoArchivoPort almacenamiento, PlatformTransactionManager manager) {
        this.almacenamiento = almacenamiento;
        this.transaccion = new TransactionTemplate(manager);
    }

    public MaterialApoyo crear(String nombre, TipoMaterial tipo, ArchivoSubido archivo,
                              Function<String, MaterialApoyo> persistir) {
        validar(nombre, tipo, archivo);
        return transaccion.execute(status -> {
            String referencia = almacenamiento.guardar(archivo.contenido());
            // También compensa fallos al hacer flush/commit, después de retornar persistir.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int estado) {
                    if (estado != STATUS_COMMITTED) {
                        try { almacenamiento.compensar(referencia); }
                        catch (RuntimeException ex) { log.error("Falló la compensación de archivo {}", referencia, ex); }
                    }
                }
            });
            return persistir.apply(referencia);
        });
    }

    public ArchivoDescarga descargar(MaterialApoyo material) {
        if (material.url() == null || !material.url().startsWith(FormatoArchivo.PREFIJO))
            throw new ArchivoException(NO_ENCONTRADO, "El material no contiene un archivo administrado");
        byte[] contenido = almacenamiento.leer(material.url());
        String mime = FormatoArchivo.mime(contenido);
        if ((material.tipo() != TipoMaterial.PDF && material.tipo() != TipoMaterial.IMAGEN)
                || (material.tipo() == TipoMaterial.PDF) != mime.equals("application/pdf"))
            throw new ArchivoException(INVALIDO, "El contenido almacenado es incompatible con el tipo del material");
        String extension = switch (mime) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> throw new ArchivoException(INVALIDO, "Formato inválido");
        };
        // No conserva el nombre físico ni el nombre original aportado por el cliente.
        String nombre = material.nombre().replaceAll("[\\p{Cntrl}\\\\/\";]", "_");
        return new ArchivoDescarga(nombre + extension, mime, contenido);
    }

    private void validar(String nombre, TipoMaterial tipo, ArchivoSubido archivo) {
        if (nombre == null || nombre.isBlank() || nombre.length() > 150)
            throw new ArchivoException(INVALIDO, "El nombre es obligatorio y no puede superar 150 caracteres");
        if (tipo != TipoMaterial.PDF && tipo != TipoMaterial.IMAGEN)
            throw new ArchivoException(INVALIDO, "Solo PDF e IMAGEN admiten archivos locales");
        if (archivo == null || archivo.contenido() == null || archivo.contenido().length == 0)
            throw new ArchivoException(INVALIDO, "El archivo está vacío");
        if (archivo.contenido().length > FormatoArchivo.MAX_BYTES)
            throw new ArchivoException(DEMASIADO_GRANDE, "El archivo supera 10 MB");
        String detectado = FormatoArchivo.mime(archivo.contenido());
        if (!detectado.equals(archivo.mime()) || (tipo == TipoMaterial.PDF) != detectado.equals("application/pdf"))
            throw new ArchivoException(INVALIDO, "El tipo, MIME y contenido del archivo son incompatibles");
    }
}
