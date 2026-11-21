package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.CodigoAccesoRepository;
import cl.casol.backend.identidad.domain.CodigoAcceso;
import cl.casol.backend.identidad.infrastructure.persistence.entity.CodigoAccesoEntity;
import cl.casol.backend.identidad.infrastructure.persistence.repository.CodigoAccesoJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adaptador de base de datos para código acceso.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Repository
public class CodigoAccesoRepositoryAdapter implements CodigoAccesoRepository {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    // Bloquea usuario.
    public void bloquearUsuario(Integer usuarioId) {
        em.find(cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity.class,
                usuarioId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    // Cuenta desde.
    public long contarDesde(Integer usuarioId, java.time.LocalDateTime desde) {
        return repository.findByUsuarioIdAndCreadoEnGreaterThanEqual(usuarioId, desde).size();
    }

    // Indica si está vigente.
    public boolean vigente(Long codigoId, java.time.LocalDateTime ahora) {
        return repository.findById(codigoId).map(this::toDomain)
                .filter(c -> c.disponible(ahora, CodigoAcceso.MAX_INTENTOS)).isPresent();
    }

    private final CodigoAccesoJpaRepository repository;

    public CodigoAccesoRepositoryAdapter(CodigoAccesoJpaRepository repository) {
        this.repository = repository;
    }

    // Busca último.
    @Override
    public Optional<CodigoAcceso> buscarUltimo(Integer usuarioId) {
        return repository.findFirstByUsuarioIdOrderByIdDesc(usuarioId).map(this::toDomain);
    }

    // Busca pendiente.
    @Override
    public Optional<CodigoAcceso> buscarPendiente(Integer usuarioId) {
        return repository.findFirstByUsuarioIdAndUsadoFalseOrderByIdDesc(usuarioId).map(this::toDomain);
    }

    // Invalida pendientes.
    @Override
    public void invalidarPendientes(Integer usuarioId) {
        repository.invalidarPendientes(usuarioId);
    }

    // Guarda el código.
    @Override
    public CodigoAcceso guardar(CodigoAcceso codigo) {
        CodigoAccesoEntity guardado = repository.save(new CodigoAccesoEntity(
                codigo.id(), codigo.usuarioId(), codigo.codigoHash(), codigo.creadoEn(),
                codigo.expiraEn(), codigo.intentos(), codigo.usado()));
        return toDomain(guardado);
    }

    // Convierte la entidad al modelo del dominio.
    private CodigoAcceso toDomain(CodigoAccesoEntity e) {
        return new CodigoAcceso(e.getId(), e.getUsuarioId(), e.getCodigoHash(), e.getCreadoEn(),
                e.getExpiraEn(), e.getIntentos(), e.isUsado());
    }
}
