package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.catalogo.application.port.out.*;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.UltimaSolucionActivaException;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Concurrent application-service contract test. The port models a transaction-held parent lock;
// it does not replace integration testing of InnoDB locks on an isolated MySQL database.
class SolucionConcurrenciaQa009Test {
    final ReentrantLock lock=new ReentrantLock();
    final AtomicReference<EstadoConocimiento> estado=new AtomicReference<>();
    final ConcurrentMap<Integer,Solucion> filas=new ConcurrentHashMap<>();
    final ConocimientoRepository conocimientos=mock(ConocimientoRepository.class);
    final SolucionRepository soluciones=mock(SolucionRepository.class);
    final IndexarConocimientoService indexador=mock(IndexarConocimientoService.class);
    final AdministrarSolucionService admin=new AdministrarSolucionService(conocimientos,soluciones,indexador);
    MantenerConocimientoService mantener;
    void setup(EstadoConocimiento inicial,int cantidad) {
        estado.set(inicial);
        for(int i=1;i<=cantidad;i++) filas.put(i,new Solucion(i,4,"D",TipoSolucion.PASOS,i,"Título",true,false));
        Conocimiento c=mock(Conocimiento.class);
        when(c.getId()).thenReturn(4); when(c.getEstado()).thenAnswer(i -> estado.get());
        when(c.getTitulo()).thenReturn("Caso"); when(c.getDescripcion()).thenReturn("D");
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(c));
        doAnswer(i -> { if (!lock.tryLock(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout de bloqueo"); return null; })
            .when(conocimientos).bloquear(4);
        when(soluciones.bloquearPorConocimiento(4)).thenAnswer(i -> new ArrayList<>(filas.values()));
        when(soluciones.guardar(any())).thenAnswer(i -> { Solucion s=i.getArgument(0); filas.put(s.id(),s); return s; });
        when(conocimientos.guardar(any())).thenAnswer(i -> { Conocimiento n=i.getArgument(0); estado.set(n.getEstado()); return n; });
        UsuarioRepository usuarios=mock(UsuarioRepository.class);
        when(usuarios.buscarPorEmail("admin")).thenReturn(Optional.of(mock(Usuario.class)));
        mantener=new MantenerConocimientoService(conocimientos,mock(HardwareRepository.class),mock(SistemaRepository.class),
            mock(ModuloRepository.class),mock(FrecuenciaRepository.class),usuarios,indexador,soluciones);
    }
    List<String> competir(Runnable a,Runnable b) throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            CountDownLatch listos=new CountDownLatch(2),salida=new CountDownLatch(1);
            Callable<String> ca=accion(a,listos,salida),cb=accion(b,listos,salida);
            Future<String> fa=executor.submit(ca),fb=executor.submit(cb);
            assertTrue(listos.await(5,TimeUnit.SECONDS)); salida.countDown();
            return List.of(fa.get(10,TimeUnit.SECONDS),fb.get(10,TimeUnit.SECONDS));
        }
    }
    Callable<String> accion(Runnable tarea,CountDownLatch listos,CountDownLatch salida) {
        return () -> { listos.countDown(); salida.await();
            try { tarea.run(); return "ok"; }
            catch(UltimaSolucionActivaException e) { return "rn08"; }
            finally { if(lock.isHeldByCurrentThread()) lock.unlock(); }
        };
    }
    @Test void dosDesactivacionesConcurrentesConservanUnaActiva() throws Exception {
        setup(EstadoConocimiento.PUBLICADO,2);
        List<String> resultados=competir(()->admin.cambiarEstado(4,1,false),()->admin.cambiarEstado(4,2,false));
        assertEquals(1,resultados.stream().filter("ok"::equals).count());
        assertEquals(1,resultados.stream().filter("rn08"::equals).count());
        assertEquals(1,filas.values().stream().filter(Solucion::activo).count());
    }
    @Test void publicarYDesactivarConcurrentementeNoDejanPublicadoSinActivas() throws Exception {
        setup(EstadoConocimiento.BORRADOR,1);
        List<String> resultados=competir(()->mantener.cambiarEstado(4,EstadoConocimiento.PUBLICADO,"admin"),
            ()->admin.cambiarEstado(4,1,false));
        assertEquals(1,resultados.stream().filter("ok"::equals).count());
        assertEquals(1,resultados.stream().filter("rn08"::equals).count());
        assertTrue(estado.get()!=EstadoConocimiento.PUBLICADO || filas.values().stream().anyMatch(Solucion::activo));
    }
}
