package cl.casol.backend.catalogo.application.service;
import cl.casol.backend.catalogo.application.port.out.CatalogoAdministrativoRepository;
import cl.casol.backend.catalogo.domain.*;
import cl.casol.backend.catalogo.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AdministrarCatalogosService {
    private final CatalogoAdministrativoRepository repository;
    public AdministrarCatalogosService(CatalogoAdministrativoRepository repository) { this.repository = repository; }
    public List<CatalogoAdministrativo> listar(TipoCatalogo tipo) { return repository.listar(tipo); }
    public CatalogoAdministrativo buscar(TipoCatalogo tipo, Integer id) {
        return repository.buscar(tipo,id).orElseThrow(() -> new CatalogoNoEncontradoException(tipo.name(),id));
    }
    @Transactional
    public CatalogoAdministrativo guardar(TipoCatalogo tipo, Integer id, CatalogoAdministrativo datos) {
        // Serialize writes using the existing rows; no new uniqueness constraints or tables.
        // Lock the parent system first for module writes, also serializing an initially empty module catalog.
        if (tipo == TipoCatalogo.modulos) repository.bloquear(TipoCatalogo.sistemas);
        repository.bloquear(tipo);
        CatalogoAdministrativo actual = id == null ? null : buscar(tipo,id);
        String nombre = datos.nombre() == null ? "" : datos.nombre().trim();
        int limite = tipo == TipoCatalogo.modulos ? 120 : 100;
        if (nombre.isEmpty() || nombre.length() > limite) throw new CatalogoInvalidoException("Nombre obligatorio; máximo " + limite + " caracteres");
        if (repository.listar(tipo).stream().anyMatch(r -> !Objects.equals(r.id(),id)
                && r.nombre().trim().equalsIgnoreCase(nombre)
                && (tipo != TipoCatalogo.modulos || Objects.equals(r.sistemaId(),datos.sistemaId()))))
            throw new CatalogoConflictoException("Ya existe un registro con ese nombre");
        if (tipo == TipoCatalogo.modulos) {
            if (datos.sistemaId() == null) throw new CatalogoInvalidoException("Debe seleccionar un sistema");
            CatalogoAdministrativo sistema = buscar(TipoCatalogo.sistemas,datos.sistemaId());
            if (!sistema.activo() && (actual == null || !Objects.equals(actual.sistemaId(),datos.sistemaId()) || (!actual.activo() && datos.activo())))
                throw new CatalogoInvalidoException("El sistema debe estar activo");
            if (actual != null && !Objects.equals(actual.sistemaId(),datos.sistemaId()) && repository.moduloReferenciado(id))
                throw new CatalogoConflictoException("No puede cambiar el sistema de un módulo usado en conocimientos");
        }
        if (tipo == TipoCatalogo.frecuencias && (datos.orden() == null || datos.orden() < 1))
            throw new CatalogoInvalidoException("El orden debe ser mayor que cero");
        // PUT parcial de asociaciones: omitido/null conserva; [] desvincula explícitamente.
        List<Integer> sistemas = datos.sistemaIds() != null ? datos.sistemaIds()
                : tipo == TipoCatalogo.hardware && actual != null ? actual.sistemaIds() : List.of();
        if (tipo == TipoCatalogo.hardware) {
            if (new HashSet<>(sistemas).size() != sistemas.size()) throw new CatalogoInvalidoException("Sistemas duplicados");
            for (Integer sistemaId : sistemas) {
                if (sistemaId == null) throw new CatalogoInvalidoException("Sistema inválido");
                CatalogoAdministrativo sistema = buscar(TipoCatalogo.sistemas,sistemaId);
                if (!sistema.activo() && (actual == null || !actual.sistemaIds().contains(sistemaId)))
                    throw new CatalogoInvalidoException("No puede asociar un sistema inactivo");
            }
        }
        CatalogoAdministrativo guardado = repository.guardar(tipo,new CatalogoAdministrativo(id,nombre,datos.activo(),
                datos.sistemaOperativo(),datos.descripcion(),datos.sistemaId(),datos.orden(),sistemas));
        repository.reindexarReferencias(tipo,guardado.id());
        return guardado;
    }
    @Transactional
    public CatalogoAdministrativo cambiarEstado(TipoCatalogo tipo, Integer id, boolean activo) {
        if (tipo == TipoCatalogo.modulos) repository.bloquear(TipoCatalogo.sistemas);
        repository.bloquear(tipo);
        CatalogoAdministrativo r = buscar(tipo,id);
        return guardar(tipo,id,new CatalogoAdministrativo(id,r.nombre(),activo,r.sistemaOperativo(),r.descripcion(),r.sistemaId(),r.orden(),r.sistemaIds()));
    }
}
