package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.*;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.*;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.ConocimientoMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.ConocimientoJpaRepository;
import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import cl.casol.backend.identidad.infrastructure.persistence.repository.UsuarioJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ConocimientoRepositoryAdapter implements ConocimientoRepository {
    private final ConocimientoJpaRepository repository;
    private final HardwareJpaRepository hardware;
    private final SistemaJpaRepository sistemas;
    private final ModuloJpaRepository modulos;
    private final FrecuenciaJpaRepository frecuencias;
    private final UsuarioJpaRepository usuarios;

    public ConocimientoRepositoryAdapter(ConocimientoJpaRepository repository, HardwareJpaRepository hardware,
            SistemaJpaRepository sistemas, ModuloJpaRepository modulos,
            FrecuenciaJpaRepository frecuencias, UsuarioJpaRepository usuarios) {
        this.repository = repository; this.hardware = hardware; this.sistemas = sistemas;
        this.modulos = modulos; this.frecuencias = frecuencias; this.usuarios = usuarios;
    }

    @Override
    public List<Conocimiento> buscarTodos() {
        return repository.findAllByOrderByFechaCreacionDesc().stream().map(ConocimientoMapper::toDomain).toList();
    }

    @Override
    public Optional<Conocimiento> buscarPorId(Integer id) {
        return repository.findById(id).map(ConocimientoMapper::toDomain);
    }

    @Override
    public Conocimiento guardar(Conocimiento c) {
        HardwareEntity hw = c.getHardware() == null ? null : hardware.getReferenceById(c.getHardware().id());
        SistemaEntity sis = c.getSistema() == null ? null : sistemas.getReferenceById(c.getSistema().id());
        ModuloEntity mod = c.getModulo() == null ? null : modulos.getReferenceById(c.getModulo().id());
        FrecuenciaEntity fre = c.getFrecuencia() == null ? null : frecuencias.getReferenceById(c.getFrecuencia().id());
        UsuarioEntity creador = usuarios.getReferenceById(c.getCreadoPor().getId());
        UsuarioEntity modificador = c.getModificadoPor() == null ? null
                : usuarios.getReferenceById(c.getModificadoPor().getId());
        return ConocimientoMapper.toDomain(repository.save(
                ConocimientoMapper.toEntity(c, hw, sis, mod, fre, creador, modificador)));
    }
}
