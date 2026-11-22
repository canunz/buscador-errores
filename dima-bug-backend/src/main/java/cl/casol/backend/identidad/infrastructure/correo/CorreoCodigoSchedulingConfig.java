package cl.casol.backend.identidad.infrastructure.correo;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configuración de correo código scheduling.
 * Registra los beans que Spring necesita al iniciar.
 */
@Configuration
public class CorreoCodigoSchedulingConfig {
    // Devuelve task scheduler.
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        // Conserva el scheduler por defecto de un hilo para los trabajos existentes.
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("seguimiento-");
        return scheduler;
    }
    /** No ocupa el scheduler de seguimiento de evidencias durante llamadas a Google. */
    @Bean
    public ThreadPoolTaskScheduler authCorreoScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("correo-autenticacion-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
