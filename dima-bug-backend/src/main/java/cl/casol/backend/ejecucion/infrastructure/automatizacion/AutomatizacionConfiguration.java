package cl.casol.backend.ejecucion.infrastructure.automatizacion;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling
public class AutomatizacionConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class) public Clock relojAutomatizacion(){return Clock.systemUTC();}
}
