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
    private final IndexarConocimientoService indexador;
    private final cl.casol.backend.conocimiento.application.port.out.SolucionRepository soluciones;

    public MantenerConocimientoService(ConocimientoRepository conocimientos, HardwareRepository hardware,
            SistemaRepository sistemas, ModuloRepository modulos, FrecuenciaRepository frecuencias,
            UsuarioRepository usuarios, IndexarConocimientoService indexador, cl.casol.backend.conocimiento.application.port.out.SolucionRepository soluciones) {
        this.conocimientos = conocimientos;
        this.hardware = hardware;
        this.sistemas = sistemas;
        this.modulos = modulos;
        this.frecuencias = frecuencias;
        this.usuarios = usuarios;
        this.indexador = indexador;
        this.soluciones = soluciones;
    }

    public List<Conocimiento> listar() { return conocimientos.buscarTodos(); }

    public Conocimiento buscar(Integer id) {
        return conocimientos.buscarPorId(id).orElseThrow(() -> new ConocimientoNoEncontradoException(id));
    }

    @Transactional
    public Conocimiento crear(String titulo, String descripcion, Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId, String comentario, String emailAutenticado) {
        validarTitulo(titulo);
        validarComentario(comentario);
        Clasificacion clasificacion = validarClasificacion(hardwareId, sistemaId, moduloId, frecuenciaId);
        Usuario usuario = obtenerUsuario(emailAutenticado);
        LocalDateTime ahora = LocalDateTime.now();
        Conocimiento guardado = conocimientos.guardar(new Conocimiento(null, titulo, descripcion, EstadoConocimiento.BORRADOR,
                clasificacion.hardware(), clasificacion.sistema(), clasificacion.modulo(),
                clasificacion.frecuencia(), comentario, usuario, ahora, null, null));
        indexador.indexar(guardado.getId());
        return guardado;
    }

    @Transactional
    public Conocimiento modificar(Integer id, String titulo, String descripcion, Integer hardwareId,
            Integer sistemaId, Integer moduloId, Integer frecuenciaId, String comentario,
            String emailAutenticado) {
        validarTitulo(titulo);
        validarComentario(comentario);
        conocimientos.bloquear(id);
        Conocimiento actual = buscar(id);
        boolean mismaClasificacion = java.util.Objects.equals(hardwareId, actual.getHardware()==null?null:actual.getHardware().id())
                && java.util.Objects.equals(sistemaId, actual.getSistema()==null?null:actual.getSistema().id())
                && java.util.Objects.equals(moduloId, actual.getModulo()==null?null:actual.getModulo().id())
                && java.util.Objects.equals(frecuenciaId, actual.getFrecuencia()==null?null:actual.getFrecuencia().id());
        Clasificacion clasificacion = mismaClasificacion
                ? new Clasificacion(actual.getHardware(),actual.getSistema(),actual.getModulo(),actual.getFrecuencia())
                : validarClasificacion(hardwareId, sistemaId, moduloId, frecuenciaId);
        Usuario usuario = obtenerUsuario(emailAutenticado);
        Conocimiento guardado = conocimientos.guardar(new Conocimiento(actual.getId(), titulo, descripcion, actual.getEstado(),
                clasificacion.hardware(), clasificacion.sistema(), clasificacion.modulo(),
                clasificacion.frecuencia(), comentario, actual.getCreadoPor(), actual.getFechaCreacion(),
                usuario, LocalDateTime.now()));
        indexador.indexar(id);
        return guardado;
    }

    @Transactional
    public Conocimiento cambiarEstado(Integer id, EstadoConocimiento estado, String emailAutenticado) {
        conocimientos.bloquear(id);
        Conocimiento actual = buscar(id);
        validarComentario(actual.getComentario());
        if (estado == EstadoConocimiento.PUBLICADO
                && soluciones.bloquearPorConocimiento(id).stream().noneMatch(cl.casol.backend.conocimiento.domain.Solucion::activo))
            throw new cl.casol.backend.conocimiento.domain.exception.UltimaSolucionActivaException();
        Usuario usuario = obtenerUsuario(emailAutenticado);
        Conocimiento guardado = conocimientos.guardar(new Conocimiento(actual.getId(), actual.getTitulo(),
                actual.getDescripcion(), estado, actual.getHardware(), actual.getSistema(), actual.getModulo(),
                actual.getFrecuencia(), actual.getComentario(), actual.getCreadoPor(),
                actual.getFechaCreacion(), usuario, LocalDateTime.now()));
        indexador.indexar(id);
        return guardado;
    }

    @Transactional
    public void eliminar(Integer id, String emailAutenticado) {
        conocimientos.bloquear(id);
        Conocimiento actual = conocimientos.buscarPorIdIncluidoEliminado(id)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(id));
        if (actual.getEstado() == EstadoConocimiento.ELIMINADO) return;
        validarComentario(actual.getComentario());

        Usuario usuario = obtenerUsuario(emailAutenticado);
        conocimientos.guardar(new Conocimiento(actual.getId(), actual.getTitulo(), actual.getDescripcion(),
                EstadoConocimiento.ELIMINADO, actual.getHardware(), actual.getSistema(), actual.getModulo(),
                actual.getFrecuencia(), actual.getComentario(), actual.getCreadoPor(), actual.getFechaCreacion(),
                usuario, LocalDateTime.now()));
        indexador.eliminar(id);
    }

    private void validarComentario(String comentario) {
        if (comentario != null && comentario.length() > 500)
            throw new ClasificacionInvalidaException("Comentario: máximo 500 caracteres");
    }

    private void validarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank() || titulo.length() > 150)
            throw new ClasificacionInvalidaException("Título obligatorio; máximo 150 caracteres");
    }

    private Usuario obtenerUsuario(String email) {
        return usuarios.buscarPorEmail(email).orElseThrow(UsuarioNoEncontradoException::new);
    }

    private Clasificacion validarClasificacion(Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId) {
        Hardware hardwareEncontrado = hardwareId == null ? null : hardware.buscarActivoPorId(hardwareId)
                .orElseThrow(() -> invalida("Hardware inexistente o inactivo"));
        if (hardwareId != null && sistemaId != null && hardware.buscarSistemasActivosPorHardware(hardwareId).stream().noneMatch(s -> sistemaId.equals(s.id())))
            throw invalida("El sistema no está asociado al hardware informado");
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
