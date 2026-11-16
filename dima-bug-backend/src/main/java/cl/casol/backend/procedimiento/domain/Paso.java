package cl.casol.backend.procedimiento.domain;

public record Paso(Integer id, Integer procedimientoId, Integer orden, String instruccion,
        boolean esCritico, ModoEjecucionPaso modoEjecucion, String mensajeEvidencia,ConfiguracionPaso configuracion) {
    public Paso {
        if(modoEjecucion==null) modoEjecucion=ModoEjecucionPaso.MANUAL;
        if(mensajeEvidencia!=null && mensajeEvidencia.length()>2000)
            throw new cl.casol.backend.procedimiento.domain.exception.EstadoProcedimientoInvalidoException("El mensaje no puede superar 2000 caracteres");
        configuracion=ConfiguracionPaso.resolver(configuracion,modoEjecucion,esCritico,mensajeEvidencia);
        if(modoEjecucion.esEvidencia() && configuracion.evidenciasRequeridas().isEmpty())throw new cl.casol.backend.procedimiento.domain.exception.EstadoProcedimientoInvalidoException("Selecciona al menos una categoría de evidencia");
    }
    public Paso(Integer id,Integer procedimientoId,Integer orden,String instruccion,boolean critico,ModoEjecucionPaso modo,String mensaje){this(id,procedimientoId,orden,instruccion,critico,modo,mensaje,null);}
    public String revisionConfiguracion(){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((modoEjecucion+"|"+esCritico+"|"+configuracion).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public Paso(Integer id,Integer procedimientoId,Integer orden,String instruccion,boolean esCritico) {
        this(id,procedimientoId,orden,instruccion,esCritico,ModoEjecucionPaso.MANUAL,null,null);
    }
}
