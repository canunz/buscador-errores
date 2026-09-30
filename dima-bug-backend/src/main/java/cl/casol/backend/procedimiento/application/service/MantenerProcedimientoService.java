package cl.casol.backend.procedimiento.application.service;

import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.UsuarioNoEncontradoException;
import cl.casol.backend.procedimiento.application.port.out.ProcedimientoRepository;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MantenerProcedimientoService {
    private final ProcedimientoRepository procedimientos;
    private final UsuarioRepository usuarios;

    public MantenerProcedimientoService(ProcedimientoRepository procedimientos, UsuarioRepository usuarios) {
        this.procedimientos = procedimientos; this.usuarios = usuarios;
    }

    public List<Procedimiento> listar() { return procedimientos.buscarTodos(); }

    public Procedimiento buscar(Integer id) {
        return procedimientos.buscarPorId(id)
                .orElseThrow(() -> new ProcedimientoNoEncontradoException(id));
    }

    @Transactional
    public Procedimiento crear(String nombre, String descripcion, String emailAutenticado) {
        Usuario usuario = obtenerUsuario(emailAutenticado);
        return procedimientos.guardar(new Procedimiento(null, nombre, descripcion,
                EstadoProcedimiento.BORRADOR, usuario, LocalDateTime.now(), null, null));
    }

    @Transactional
    public Procedimiento modificar(Integer id, String nombre, String descripcion, String emailAutenticado) {
        Procedimiento actual = buscar(id);
        Usuario usuario = obtenerUsuario(emailAutenticado);
        return procedimientos.guardar(new Procedimiento(actual.id(), nombre, descripcion, actual.estado(),
                actual.creadoPor(), actual.fechaCreacion(), usuario, LocalDateTime.now()));
    }

    @Transactional
    public Procedimiento publicar(Integer id, String emailAutenticado) {
        Procedimiento actual = buscar(id);
        if (actual.estado() != EstadoProcedimiento.BORRADOR) {
            throw new EstadoProcedimientoInvalidoException("Solo se puede publicar un procedimiento en BORRADOR");
        }
        Usuario usuario = obtenerUsuario(emailAutenticado);
        return procedimientos.guardar(new Procedimiento(actual.id(), actual.nombre(), actual.descripcion(),
                EstadoProcedimiento.PUBLICADO, actual.creadoPor(), actual.fechaCreacion(), usuario,
                LocalDateTime.now()));
    }

    private Usuario obtenerUsuario(String email) {
        return usuarios.buscarPorEmail(email).orElseThrow(UsuarioNoEncontradoException::new);
    }
}
