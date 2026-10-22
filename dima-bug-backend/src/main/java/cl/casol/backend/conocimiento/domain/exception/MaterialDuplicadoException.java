package cl.casol.backend.conocimiento.domain.exception;
public class MaterialDuplicadoException extends RuntimeException {
    public MaterialDuplicadoException(){super("El conocimiento destino ya contiene ese archivo o enlace");}
}
