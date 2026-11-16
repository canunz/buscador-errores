package cl.casol.backend.procedimiento.domain;

/** La criticidad es independiente del modo de ejecución. */
public enum ModoEjecucionPaso {
    MANUAL, AUTOMATICO, VALIDACION_EXTERNA, INFORMATIVO,
    MANUAL_GUIADO, SOLICITAR_EVIDENCIA, ENVIAR_CORREO;
    public boolean esEvidencia(){return this==VALIDACION_EXTERNA || this==SOLICITAR_EVIDENCIA;}
    public boolean esManual(){return this==MANUAL || this==MANUAL_GUIADO;}
    public boolean tieneAccion(){return esEvidencia() || this==ENVIAR_CORREO || this==AUTOMATICO;}
}
