package cl.casol.backend.procedimiento.domain;

/** Cada publicación conserva sus filas; las revisiones nuevas se editan como borradores. */
public record DatosEditorialesProcedimiento(Integer sistemaId,Integer moduloId,String informacionComplementaria,
        Integer origenId,int revision,boolean vigente) {
    public static DatosEditorialesProcedimiento inicial(){return new DatosEditorialesProcedimiento(null,null,null,null,1,true);}
    public DatosEditorialesProcedimiento vigencia(boolean valor){return new DatosEditorialesProcedimiento(sistemaId,moduloId,informacionComplementaria,origenId,revision,valor);}
}
