package cl.casol.backend.seguimiento.infrastructure.persistence.adapter;

import cl.casol.backend.seguimiento.domain.*;
import cl.casol.backend.seguimiento.infrastructure.persistence.entity.ResultadoSolucionEntity;
import cl.casol.backend.seguimiento.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultadoSolucionRepositoryAdapterTest {
 ResultadoSolucionJpaRepository jpa; ResultadoSolucionRepositoryAdapter adapter;
 @BeforeEach void setup(){jpa=mock(ResultadoSolucionJpaRepository.class);adapter=new ResultadoSolucionRepositoryAdapter(jpa);}
 @Test void guardarSiempreUsaSave(){var e=entity(1,5,7,true);when(jpa.save(any())).thenReturn(e);assertEquals(1,adapter.guardar(new ResultadoSolucion(null,5,7,true,null,LocalDateTime.now())).id());verify(jpa).save(any());}
 @Test void historialRespetaOrdenDelRepositorio(){when(jpa.findBySolucionIdOrderByFechaDescIdDesc(5)).thenReturn(List.of(entity(3,5,7,false),entity(2,5,7,true)));assertEquals(List.of(3,2),adapter.buscarPorSolucionOrdenados(5).stream().map(ResultadoSolucion::id).toList());}
 @Test void tresTrueUnoFalseDa75(){projection(4,3,1);EfectividadSolucion e=adapter.calcularEfectividad(5);assertEquals(4,e.totalAplicaciones());assertEquals(3,e.totalFunciono());assertEquals(1,e.totalNoFunciono());assertEquals(75.0,e.porcentajeEfectividad());}
 @Test void todosTrueDa100(){projection(3,3,0);assertEquals(100.0,adapter.calcularEfectividad(5).porcentajeEfectividad());}
 @Test void todosFalseDaCero(){projection(3,0,3);assertEquals(0.0,adapter.calcularEfectividad(5).porcentajeEfectividad());}
 @Test void sinResultadosDaPorcentajeNull(){projection(0,0,0);EfectividadSolucion e=adapter.calcularEfectividad(5);assertEquals(0,e.totalAplicaciones());assertNull(e.porcentajeEfectividad());}
 @Test void calculoNoUsaDivisionEntera(){projection(3,2,1);assertEquals(66.66666666666667,adapter.calcularEfectividad(5).porcentajeEfectividad());}
 @Test void consultaAgregaSoloIdSolicitado(){projection(1,1,0);adapter.calcularEfectividad(5);verify(jpa).calcularEfectividad(5);verify(jpa,never()).calcularEfectividad(6);}
 void projection(long total,long ok,long no){EfectividadProjection p=mock(EfectividadProjection.class);when(p.getTotalAplicaciones()).thenReturn(total);when(p.getTotalFunciono()).thenReturn(ok);when(p.getTotalNoFunciono()).thenReturn(no);when(jpa.calcularEfectividad(5)).thenReturn(p);}
 ResultadoSolucionEntity entity(int id,int s,int u,boolean ok){return new ResultadoSolucionEntity(id,s,u,ok,null,LocalDateTime.now());}
}
