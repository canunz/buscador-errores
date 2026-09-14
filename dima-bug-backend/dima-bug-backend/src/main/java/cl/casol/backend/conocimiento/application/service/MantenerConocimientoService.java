package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.catalogo.application.port.out.*;
import cl.casol.backend.catalogo.domain.*;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import cl.casol.backend.conocimiento.domain.exception.ClasificacionInvalidaException;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.UsuarioNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MantenerConocimientoService {
    private final ConocimientoRepository conocimientos;
    private final HardwareRepository hardware;
    private final SistemaRepository sistemas;
    private final ModuloRepository modulos;
    private final FrecuenciaRepository frecuencias;
    private final UsuarioRepository usuarios;

    public MantenerConocimientoService(ConocimientoRepository conocimientos, HardwareRepository hardware,
            SistemaRepository sistemas, ModuloRepository modulos, FrecuenciaRepository frecuencias,
            UsuarioRepository usuarios) {
        this.conocimientos = conocimientos;
        this.hardware = hardware;
        this.sistemas = sistemas;
        this.modulos = modulos;
        this.frecuencias = frecuencias;
        this.usuarios = usuarios;
    }

    public List<Conocimiento> listar() { return conocimientos.buscarTodos(); }

    public Conocimiento buscar(Integer id) {
        return conocimientos.buscarPorId(id).orElseThrow(() -> new ConocimientoNoEncontradoException(id));
    }

    @Transactional
    public Conocimiento crear(String titulo, String descripcion, Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId, String comentario, String emailAutenticado) {
        Clasificacion clasificacion = validarClasificacion(hardwareId, sistemaId, moduloId, frecuenciaId);
        Usuario usuario = obtenerUsuario(emailAutenticado);
        LocalDateTime ahora = LocalDateTime.now();
        return conocimientos.guardar(new Conocimiento(null, titulo, descripcion, EstadoConocimiento.BORRADOR,
                clasificacion.hardware(), clasificacion.sistema(), clasificacion.modulo(),
                clasificacion.frecuencia(), comentario, usuario, ahora, null, null));
    }

    @Transactional
    public Conocimiento modificar(Integer id, String titulo, String descripcion, Integer hardwareId,
            Integer sistemaId, Integer moduloId, Integer frecuenciaId, String comentario,
            String emailAutenticado) {
        Conocimiento actual = buscar(id);
        Clasificacion clasificacion = validarClasificacion(hardwareId, sistemaId, moduloId, frecuenciaId);
        Usuario usuario = obtenerUsuario(emailAutenticado);
        return conocimientos.guardar(new Conocimiento(actual.getId(), titulo, descripcion, actual.getEstado(),
                clasificacion.hardware(), clasificacion.sistema(), clasificacion.modulo(),
                clasificacion.frecuencia(), comentario, actual.getCreadoPor(), actual.getFechaCreacion(),
                usuario, LocalDateTime.now()));
    }

    @Transactional
    public Conocimiento cambiarEstado(Integer id, EstadoConocimiento estado, String emailAutenticado) {
        Conocimiento actual = buscar(id);
        Usuario usuario = obtenerUsuario(emailAutenticado);
        return conocimientos.guardar(new Conocimiento(actual.getId(), actual.getTitulo(),
                actual.getDescripcion(), estado, actual.getHardware(), actual.getSistema(), actual.getModulo(),
                actual.getFrecuencia(), actual.getComentario(), actual.getCreadoPor(),
                actual.getFechaCreacion(), usuario, LocalDateTime.now()));
    }

    private Usuario obtenerUsuario(String email) {
        return usuarios.buscarPorEmail(email).orElseThrow(UsuarioNoEncontradoException::new);
    }

    private Clasificacion validarClasificacion(Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId) {
        Hardware hardwareEncontrado = hardwareId == null ? null : hardware.buscarActivoPorId(hardwareId)
                .orElseThrow(() -> invalida("Hardware inexistente o inactivo"));
        Sistema sistemaEncontrado = sistemaId == null ? null : sistemas.buscarActivoPorId(sistemaId)
                .orElseThrow(() -> invalida("Sistema inexistente o inactivo"));
        Modulo moduloEncontrado = null;
        if (moduloId != null) {
            if (sistemaId == null) throw invalida("Debe informar sistemaId cuando informa moduloId");
            moduloEncontrado = modulos.buscarActivoPorId(moduloId)
                    .orElseThrow(() -> invalida("Módulo inexistente o inactivo"));
            if (!sistemaId.equals(moduloEncontrado.sistemaId())) {
                throw invalida("El módulo no pertenece al sistema informado");
            }
        }
        Frecuencia frecuenciaEncontrada = frecuenciaId == null ? null
                : frecuencias.buscarActivaPorId(frecuenciaId)
                .orElseThrow(() -> invalida("Frecuencia inexistente o inactiva"));
        return new Clasificacion(hardwareEncontrado, sistemaEncontrado, moduloEncontrado, frecuenciaEncontrada);
    }

    private ClasificacionInvalidaException invalida(String mensaje) {
        return new ClasificacionInvalidaException(mensaje);
    }

    private record Clasificacion(Hardware hardware, Sistema sistema, Modulo modulo, Frecuencia frecuencia) { }
}
