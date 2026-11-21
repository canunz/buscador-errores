package cl.casol.backend.catalogo.infrastructure.web.dto;

import cl.casol.backend.catalogo.domain.Hardware;

/**
 * Datos que se devuelven al frontend con hardware.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record HardwareResponse(Integer id, String nombre, String sistemaOperativo) {
    // Crea el objeto a partir de los datos recibidos.
    public static HardwareResponse from(Hardware hardware) {
        return new HardwareResponse(hardware.id(), hardware.nombre(), hardware.sistemaOperativo());
    }
}
