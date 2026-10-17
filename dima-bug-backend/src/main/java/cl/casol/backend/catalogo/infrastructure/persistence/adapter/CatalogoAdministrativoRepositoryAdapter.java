package cl.casol.backend.catalogo.infrastructure.persistence.adapter;
import cl.casol.backend.catalogo.application.port.out.CatalogoAdministrativoRepository;
import cl.casol.backend.catalogo.domain.*;
import cl.casol.backend.catalogo.infrastructure.persistence.entity.*;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.*;
import cl.casol.backend.conocimiento.application.service.IndexarConocimientoService;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CatalogoAdministrativoRepositoryAdapter implements CatalogoAdministrativoRepository {
    private final EntityManager em;
    private final HardwareJpaRepository hardware;
    private final SistemaJpaRepository sistemas;
    private final ModuloJpaRepository modulos;
    private final FrecuenciaJpaRepository frecuencias;
    private final IndexarConocimientoService indexador;
    public CatalogoAdministrativoRepositoryAdapter(EntityManager em, HardwareJpaRepository hardware,
            SistemaJpaRepository sistemas, ModuloJpaRepository modulos, FrecuenciaJpaRepository frecuencias,
            IndexarConocimientoService indexador) {
        this.em=em; this.hardware=hardware; this.sistemas=sistemas; this.modulos=modulos; this.frecuencias=frecuencias; this.indexador=indexador;
    }
    private String entidad(TipoCatalogo tipo) { return switch(tipo) {
        case hardware -> "HardwareEntity"; case sistemas -> "SistemaEntity";
        case modulos -> "ModuloEntity"; case frecuencias -> "FrecuenciaEntity";
    }; }
    public void bloquear(TipoCatalogo tipo) {
        em.createQuery("select e from " + entidad(tipo) + " e order by e.id")
            .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }
    public List<CatalogoAdministrativo> listar(TipoCatalogo tipo) {
        return em.createQuery("select e from " + entidad(tipo) + " e order by e.id",Object.class).getResultList().stream().map(this::mapear).toList();
    }
    public Optional<CatalogoAdministrativo> buscar(TipoCatalogo tipo,Integer id) {
        Class<?> clase = switch(tipo) { case hardware -> HardwareEntity.class; case sistemas -> SistemaEntity.class;
            case modulos -> ModuloEntity.class; case frecuencias -> FrecuenciaEntity.class; };
        return Optional.ofNullable(em.find(clase,id)).map(this::mapear);
    }
    private CatalogoAdministrativo mapear(Object objeto) {
        if (objeto instanceof HardwareEntity h) return new CatalogoAdministrativo(h.getId(),h.getNombre(),h.isActivo(),h.getSistemaOperativo(),null,null,null,h.getSistemas().stream().map(SistemaEntity::getId).sorted().toList());
        if (objeto instanceof SistemaEntity s) return new CatalogoAdministrativo(s.getId(),s.getNombre(),s.isActivo(),null,s.getDescripcion(),null,null,List.of());
        if (objeto instanceof ModuloEntity m) return new CatalogoAdministrativo(m.getId(),m.getNombre(),m.isActivo(),null,null,m.getSistema()==null?null:m.getSistema().getId(),null,List.of());
        FrecuenciaEntity f=(FrecuenciaEntity)objeto;
        return new CatalogoAdministrativo(f.getId(),f.getNombre(),f.isActiva(),null,null,null,f.getOrden(),List.of());
    }
    public CatalogoAdministrativo guardar(TipoCatalogo tipo,CatalogoAdministrativo r) {
        Object guardado = switch(tipo) {
            case hardware -> {
                HardwareEntity h=new HardwareEntity(r.id(),r.nombre(),r.sistemaOperativo(),r.activo());
                r.sistemaIds().forEach(id -> h.getSistemas().add(sistemas.getReferenceById(id)));
                yield hardware.saveAndFlush(h);
            }
            case sistemas -> sistemas.saveAndFlush(new SistemaEntity(r.id(),r.nombre(),r.descripcion(),r.activo()));
            case modulos -> modulos.saveAndFlush(new ModuloEntity(r.id(),sistemas.getReferenceById(r.sistemaId()),r.nombre(),r.activo()));
            case frecuencias -> frecuencias.saveAndFlush(new FrecuenciaEntity(r.id(),r.nombre(),r.orden(),r.activo()));
        };
        return mapear(guardado);
    }
    public boolean moduloReferenciado(Integer id) {
        return em.createQuery("select count(c) from ConocimientoEntity c where c.modulo.id = :id",Long.class).setParameter("id",id).getSingleResult()>0;
    }
    public void reindexarReferencias(TipoCatalogo tipo,Integer id) {
        String campo=switch(tipo) { case hardware -> "hardware"; case sistemas -> "sistema"; case modulos -> "modulo"; case frecuencias -> "frecuencia"; };
        em.createQuery("select c.id from ConocimientoEntity c where c."+campo+".id = :id and c.estado <> cl.casol.backend.conocimiento.domain.EstadoConocimiento.ELIMINADO",Integer.class)
            .setParameter("id",id).getResultList().forEach(indexador::indexar);
    }
}
