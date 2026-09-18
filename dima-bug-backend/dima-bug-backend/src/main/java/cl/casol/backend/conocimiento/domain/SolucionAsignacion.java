package cl.casol.backend.conocimiento.domain;

import cl.casol.backend.identidad.domain.Departamento;
import cl.casol.backend.identidad.domain.Responsable;

public record SolucionAsignacion(Integer id, Integer solucionId, Responsable responsable,
                                 Departamento departamento, boolean principal) { }
