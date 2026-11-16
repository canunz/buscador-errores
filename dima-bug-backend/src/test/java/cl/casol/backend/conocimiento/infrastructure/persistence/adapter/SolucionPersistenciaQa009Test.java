package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.*;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.SolucionMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SolucionPersistenciaQa009Test {
    @Test void mapperPreservaTodosLosAtributosHistoricosYAdministrativos() {
        Solucion s=new Solucion(8,4,"Instrucciones",TipoSolucion.DERIVACION,3,null,false,true);
        assertEquals(s,SolucionMapper.toDomain(SolucionMapper.toEntity(s)));
        assertEquals("solucion_titulo",columna("titulo"));
        assertEquals("solucion_estado",columna("activo"));
        assertEquals("solucion_autoservicio",columna("autoservicio"));
    }
    String columna(String nombre) {
        try { return SolucionEntity.class.getDeclaredField(nombre).getAnnotation(Column.class).name(); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Test void guardarNoBorraNiPierdeEstado() {
        SolucionJpaRepository repo=mock(SolucionJpaRepository.class);
        SolucionRepositoryAdapter adapter=new SolucionRepositoryAdapter(repo);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        Solucion s=new Solucion(8,4,"D",TipoSolucion.PASOS,2,"Título",false,true);
        assertEquals(s,adapter.guardar(s)); verify(repo).save(any()); verifyNoMoreInteractions(repo);
    }
    @Test void bloqueoActualizaEntidadesAntesDeContar() {
        SolucionJpaRepository repo=mock(SolucionJpaRepository.class); EntityManager em=mock(EntityManager.class);
        SolucionRepositoryAdapter adapter=new SolucionRepositoryAdapter(repo);
        ReflectionTestUtils.setField(adapter,"entityManager",em);
        SolucionEntity e=new SolucionEntity(8,4,"D",TipoSolucion.PASOS,1,null,false,false);
        when(repo.bloquearPorConocimiento(4)).thenReturn(List.of(e));
        assertFalse(adapter.bloquearPorConocimiento(4).getFirst().activo());
        verify(em).refresh(e,LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void listadoOperacionalUsaFiltroActivoYAdministrativoConservaTodos() {
        SolucionJpaRepository repo=mock(SolucionJpaRepository.class); SolucionRepositoryAdapter adapter=new SolucionRepositoryAdapter(repo);
        adapter.buscarActivasPorConocimiento(4); verify(repo).findByConocimientoIdAndActivoTrueOrderByOrdenAsc(4);
        adapter.buscarPorConocimientoOrdenadas(4); verify(repo).findByConocimientoIdOrderByOrdenAsc(4);
    }
    @Test void repositoriosDeclaranBloqueosPesimistas() throws Exception {
        assertEquals(LockModeType.PESSIMISTIC_WRITE,ConocimientoJpaRepository.class.getMethod("bloquearPorId",Integer.class)
            .getAnnotation(org.springframework.data.jpa.repository.Lock.class).value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE,SolucionJpaRepository.class.getMethod("bloquearPorConocimiento",Integer.class)
            .getAnnotation(org.springframework.data.jpa.repository.Lock.class).value());
    }
}
