package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.CodigoAccesoRepository;
import cl.casol.backend.identidad.domain.CodigoAcceso;
import cl.casol.backend.identidad.infrastructure.persistence.entity.CodigoAccesoEntity;
import cl.casol.backend.identidad.infrastructure.persistence.repository.CodigoAccesoJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CodigoAccesoRepositoryAdapter implements CodigoAccesoRepository {

    private final CodigoAccesoJpaRepository repository;

    public CodigoAccesoRepositoryAdapter(CodigoAccesoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CodigoAcceso> buscarUltimo(Integer usuarioId) {
        return repository.findFirstByUsuarioIdOrderByIdDesc(usuarioId).map(this::toDomain);
    }

    @Override
    public Optional<CodigoAcceso> buscarPendiente(Integer usuarioId) {
        return repository.findFirstByUsuarioIdAndUsadoFalseOrderByIdDesc(usuarioId).map(this::toDomain);
    }

    @Override
    public void invalidarPendientes(Integer usuarioId) {
        repository.invalidarPendientes(usuarioId);
    }

    @Override
    public CodigoAcceso guardar(CodigoAcceso codigo) {
        CodigoAccesoEntity guardado = repository.save(new CodigoAccesoEntity(
                codigo.id(), codigo.usuarioId(), codigo.codigoHash(), codigo.creadoEn(),
                codigo.expiraEn(), codigo.intentos(), codigo.usado()));
        return toDomain(guardado);
    }

    private CodigoAcceso toDomain(CodigoAccesoEntity e) {
        return new CodigoAcceso(e.getId(), e.getUsuarioId(), e.getCodigoHash(), e.getCreadoEn(),
                e.getExpiraEn(), e.getIntentos(), e.isUsado());
    }
}
