package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.SucursalRepository;
import cl.casol.backend.identidad.domain.Sucursal;
import cl.casol.backend.identidad.domain.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdministrarSucursalesServiceTest {
    @Mock SucursalRepository repo;
    @InjectMocks AdministrarSucursalesService service;
    // Solo fixture local de validación; nunca se conecta a un buzón ni envía correo.
    static final String EMAIL="qa-sintetico-no-enviar@dimarsa.cl";
    Sucursal sucursal(boolean activa) { return new Sucursal(1,"QA_01","Sintética","Responsable QA",EMAIL,activa,3L); }
    void actual() { when(repo.buscar(1)).thenReturn(Optional.of(sucursal(true))); }

    @Test void normalizaCodigoYDominioSinInventarDireccionDelResponsable() {
        when(repo.guardar(any())).thenAnswer(i->i.getArgument(0));
        var r=service.crear(" qa_01 "," Sintética "," Responsable QA ","QA-no-enviar@DIMARSA.CL");
        assertEquals("QA_01",r.codigo());assertEquals("QA-no-enviar@dimarsa.cl",r.responsableEmail());assertTrue(r.activa());assertNull(r.version());
    }
    @ParameterizedTest @ValueSource(strings={"a@dimarsa.cl.evil","a@sub.dimarsa.cl","a@dimarsa.com","a@example.invalid","a@dimarsа.cl","a@dimarsa.cl,b@dimarsa.cl","a@dimarsa.cl\r\nBcc:b@dimarsa.cl","Nombre <a@dimarsa.cl>",".a@dimarsa.cl","a..b@dimarsa.cl"})
    void rechazaDominioFalsoMultiplesDestinatariosYControles(String email) {
        assertThrows(OrganizacionInvalidaException.class,()->service.crear("QA_01","Sintética","Responsable QA",email));
        verify(repo,never()).guardar(any());
    }
    @Test void codigoInactivoTambienReservaLaIdentidad() {
        when(repo.buscarPorCodigo("QA_01")).thenReturn(Optional.of(sucursal(false)));
        assertThrows(OrganizacionConflictoException.class,()->service.crear("qa_01","Sintética","QA",EMAIL));
        verify(repo,never()).guardar(any());
    }
    @Test void edicionObsoletaNoSobrescribeContacto() {
        actual(); assertThrows(OrganizacionConflictoException.class,()->service.editar(1,"QA_01","Otro","QA",EMAIL,2L));
        verify(repo,never()).guardar(any());
    }
    @Test void edicionExigeVersion() {
        actual();assertThrows(OrganizacionInvalidaException.class,()->service.editar(1,"QA_01","Otro","QA",EMAIL,null));
        verify(repo,never()).guardar(any());
    }
    @Test void editarConservaEstadoYVersionPersistida() {
        when(repo.buscar(1)).thenReturn(Optional.of(sucursal(false)));when(repo.guardar(any())).thenAnswer(i->i.getArgument(0));
        var r=service.editar(1,"QA_01","Nueva","QA",EMAIL,3L);
        assertFalse(r.activa());assertEquals(3L,r.version());
        var orden=inOrder(repo);orden.verify(repo).bloquear(1);orden.verify(repo).buscar(1);
    }
    @Test void consultaOperacionalSoloIncluyeActivas() {
        when(repo.listar()).thenReturn(List.of(sucursal(false),new Sucursal(2,"QA_02","Activa","QA",EMAIL,true,0L)));
        assertEquals(List.of(2),service.disponibles().stream().map(Sucursal::id).toList());
    }
    @Test void estadoIdempotenteNoCreaUnaNuevaRevision() {
        actual(); assertEquals(3L,service.estado(1,true,3L).version());verify(repo,never()).guardar(any());
    }
    @Test void desactivarNoBorraElContacto() {
        actual();when(repo.guardar(any())).thenAnswer(i->i.getArgument(0));var r=service.estado(1,false,3L);
        assertFalse(r.activa());assertEquals(EMAIL,r.responsableEmail());assertEquals(1,r.id());
    }
}
