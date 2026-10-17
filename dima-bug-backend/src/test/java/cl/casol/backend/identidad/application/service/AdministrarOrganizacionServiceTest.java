package cl.casol.backend.identidad.application.service;
import cl.casol.backend.identidad.application.port.out.AdministracionOrganizacionRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AdministrarOrganizacionServiceTest {
    AdministracionOrganizacionRepository repo=mock(AdministracionOrganizacionRepository.class);
    ApplicationEventPublisher eventos=mock(ApplicationEventPublisher.class);
    AdministrarOrganizacionService service=new AdministrarOrganizacionService(repo,eventos);
    DepartamentoAdministrativo d(boolean activo) { return new DepartamentoAdministrativo(1,"TI",activo,2L); }
    ContactoDepartamentoAdministrativo c(boolean activo) { return new ContactoDepartamentoAdministrativo(4,1,"Anexo","618",activo,3L); }
    @BeforeEach void setup() {
        when(repo.guardarDepartamento(any())).thenAnswer(i->i.getArgument(0));
        when(repo.guardarContacto(any())).thenAnswer(i->i.getArgument(0));
    }
    void padre(boolean activo) { when(repo.buscarDepartamento(1)).thenReturn(Optional.of(d(activo))); }
    void hijo(boolean activo) { when(repo.buscarContacto(4)).thenReturn(Optional.of(c(activo))); }
    @Test void creaNormalizadoActivo() { var r=service.crear(" TI "); assertEquals("TI",r.nombre()); assertTrue(r.activo()); verify(repo).bloquearEscrituras(); }
    @Test void nombreObligatorioYLimite() {
        for(String n:Arrays.asList(null,"","  ","x".repeat(256))) assertThrows(OrganizacionInvalidaException.class,()->service.crear(n));
        verify(repo,never()).guardarDepartamento(any());
    }
    @Test void duplicadoIgnoraMayusculasEspaciosEIncluyeInactivos() {
        when(repo.listarDepartamentos()).thenReturn(List.of(new DepartamentoAdministrativo(1," TI ",false,0L)));
        assertThrows(OrganizacionConflictoException.class,()->service.crear("ti")); verify(repo,never()).guardarDepartamento(any());
    }
    @Test void editarPreservaEstadoYNoColisionaConSuId() {
        padre(false); when(repo.listarDepartamentos()).thenReturn(List.of(d(false)));
        assertFalse(service.editar(1,"TI",2L).activo()); verify(eventos).publishEvent(new OrganizacionActualizada(1));
    }
    @Test void versionObsoletaNoSobrescribe() {
        padre(true); assertThrows(OrganizacionConflictoException.class,()->service.editar(1,"Nuevo",1L)); verify(repo,never()).guardarDepartamento(any());
    }
    @Test void versionObligatoria() { padre(true); assertThrows(OrganizacionInvalidaException.class,()->service.estado(1,false,null)); }
    @Test void desactivarConservaDatosYSoloEscribePadre() {
        padre(true); var r=service.estado(1,false,2L); assertFalse(r.activo()); assertEquals(1,r.id()); assertEquals("TI",r.nombre());
        verify(repo,never()).guardarContacto(any()); verify(repo,never()).listarContactos(any());
    }
    @Test void reactivarNoActivaHijos() { padre(false); assertTrue(service.estado(1,true,2L).activo()); verify(repo,never()).guardarContacto(any()); }
    @Test void estadoSinCambioNoReescribeYPermiteReindexacion() { padre(true); assertTrue(service.estado(1,true,2L).activo()); verify(repo,never()).guardarDepartamento(any()); verify(eventos).publishEvent(new OrganizacionActualizada(1)); }
    @Test void inexistente404() { assertThrows(DepartamentoNoEncontradoException.class,()->service.estado(99,false,0L)); }
    @Test void leeInactivosYSusContactosSinCambiarEstado() {
        padre(false); when(repo.listarContactos(1)).thenReturn(List.of(c(true),c(false)));
        assertEquals(2,service.contactos(1).size()); verify(repo,never()).guardarContacto(any());
    }
    @Test void creaContactoConPadreReal() { padre(true); var r=service.crearContacto(1," Anexo "," 618 ",true); assertEquals(1,r.departamentoId()); assertEquals("618",r.valor()); }
    @Test void contactoActivoNoSeCreaBajoPadreInactivo() { padre(false); assertThrows(OrganizacionConflictoException.class,()->service.crearContacto(1,"Anexo","618",true)); verify(repo,never()).guardarContacto(any()); }
    @Test void contactoInactivoPuedeCrearseBajoPadreInactivo() { padre(false); assertFalse(service.crearContacto(1,"Anexo","618",false).activo()); }
    @Test void duplicadoContactoEsContextualPorTipoValorNoNombrePersona() {
        padre(true); when(repo.listarContactos(1)).thenReturn(List.of(c(false)));
        assertThrows(OrganizacionConflictoException.class,()->service.crearContacto(1,"anexo","618",true));
        assertEquals("Correo",service.crearContacto(1,"Correo","618",true).tipo());
    }
    @Test void validaTipoValor() {
        padre(true); assertThrows(OrganizacionInvalidaException.class,()->service.crearContacto(1,"","x",true));
        assertThrows(OrganizacionInvalidaException.class,()->service.crearContacto(1,"x".repeat(51),"x",true));
        assertThrows(OrganizacionInvalidaException.class,()->service.crearContacto(1,"Anexo","x".repeat(101),true));
    }
    @Test void contactoAjenoNoSeMueveNiEdita() {
        padre(true); when(repo.buscarContacto(4)).thenReturn(Optional.of(new ContactoDepartamentoAdministrativo(4,9,"Anexo","618",true,3L)));
        assertThrows(ContactoDepartamentoNoEncontradoException.class,()->service.editarContacto(1,4,"Anexo","619",3L)); verify(repo,never()).guardarContacto(any());
    }
    @Test void edicionContactoConservaPadreEstado() { padre(false); hijo(false); var r=service.editarContacto(1,4,"Anexo","619",3L); assertFalse(r.activo()); assertEquals(1,r.departamentoId()); }
    @Test void noReactivaContactoBajoPadreInactivo() { padre(false); hijo(false); assertThrows(OrganizacionConflictoException.class,()->service.estadoContacto(1,4,true,3L)); }
    @Test void desactivaContactoBajoPadreInactivoSinCambiarPadre() { padre(false); hijo(true); assertFalse(service.estadoContacto(1,4,false,3L).activo()); verify(repo,never()).guardarDepartamento(any()); }
    @Test void contactoObsoletoNoSobrescribe() { padre(true); hijo(true); assertThrows(OrganizacionConflictoException.class,()->service.editarContacto(1,4,"Anexo","619",2L)); }
    @Test void ordenBloqueoGlobalLuegoPadre() { padre(true); hijo(true); service.estadoContacto(1,4,false,3L); var orden=inOrder(repo); orden.verify(repo).bloquearEscrituras(); orden.verify(repo).bloquearDepartamento(1); }
}
