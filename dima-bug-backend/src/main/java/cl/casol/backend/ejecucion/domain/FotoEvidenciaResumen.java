package cl.casol.backend.ejecucion.domain;

/** Metadatos de lectura: los bytes se descargan solo al abrir una fotografía. */
public record FotoEvidenciaResumen(Long id,EvidenciaFotografica.Categoria categoria,String sha256) { }
