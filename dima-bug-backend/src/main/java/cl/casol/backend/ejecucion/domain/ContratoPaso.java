package cl.casol.backend.ejecucion.domain;

import cl.casol.backend.procedimiento.domain.ModoEjecucionPaso;
import cl.casol.backend.procedimiento.domain.Paso;
import java.util.Objects;

/** Contrato capturado al iniciar, nunca reconstruido para ejecuciones históricas. */
public record ContratoPaso(int version, int orden, String instruccion,
        boolean esCritico, ModoEjecucionPaso modo,cl.casol.backend.procedimiento.domain.ConfiguracionPaso configuracion) {
    public ContratoPaso {
        if (version != 1 || orden < 1) throw new IllegalArgumentException("Contrato de paso inválido");
        Objects.requireNonNull(instruccion);
        Objects.requireNonNull(modo);
        configuracion=cl.casol.backend.procedimiento.domain.ConfiguracionPaso.resolver(configuracion,modo,esCritico,null);
    }
    public ContratoPaso(int version,int orden,String instruccion,boolean critico,ModoEjecucionPaso modo){this(version,orden,instruccion,critico,modo,null);}

    public static ContratoPaso capturar(Paso paso) {
        return new ContratoPaso(1, paso.orden(), paso.instruccion(), paso.esCritico(), paso.modoEjecucion(),paso.configuracion());
    }
}
