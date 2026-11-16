package cl.casol.backend.correo.maintenance;

import cl.casol.backend.correo.application.port.out.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.application.service.DespacharCodigosService;
import cl.casol.backend.ejecucion.application.service.AutomatizarEvidenciaService;
import cl.casol.backend.ejecucion.infrastructure.automatizacion.SeguimientoEvidenciaWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.*;

class MantenimientoCorreoTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner()
            .withUserConfiguration(DespacharCodigosService.class,SeguimientoEvidenciaWorker.class);
    private ApplicationContextRunner dependencias() {
        return runner.withBean(CorreoCodigoStore.class,()->mock(CorreoCodigoStore.class))
                .withBean(CodigoAccesoRepository.class,()->mock(CodigoAccesoRepository.class))
                .withBean(CifradoGmailPort.class,()->mock(CifradoGmailPort.class))
                .withBean(CorreoTransportePort.class,()->mock(CorreoTransportePort.class))
                .withBean(AutomatizarEvidenciaService.class,()->mock(AutomatizarEvidenciaService.class));
    }
    @Test void mantenimientoNoCreaWorkersNiRequiereSusDependencias() {
        runner.withPropertyValues("dimabug.auth.correo.habilitado=false","dimabug.automatizacion.seguimiento-habilitado=false")
                .run(c->{assertNull(c.getStartupFailure());assertEquals(0,c.getBeansOfType(DespacharCodigosService.class).size());assertEquals(0,c.getBeansOfType(SeguimientoEvidenciaWorker.class).size());});
    }
    @Test void defaultsConservanAmbosWorkers() {
        dependencias().run(c->{assertNull(c.getStartupFailure());assertEquals(1,c.getBeansOfType(DespacharCodigosService.class).size());assertEquals(1,c.getBeansOfType(SeguimientoEvidenciaWorker.class).size());});
    }
    @Test void pilotoPuedeHabilitarCodigosSinSeguimientoDeEvidencias() {
        dependencias().withPropertyValues("dimabug.auth.correo.habilitado=true","dimabug.automatizacion.seguimiento-habilitado=false")
                .run(c->{assertNull(c.getStartupFailure());assertEquals(1,c.getBeansOfType(DespacharCodigosService.class).size());assertEquals(0,c.getBeansOfType(SeguimientoEvidenciaWorker.class).size());});
    }
}
