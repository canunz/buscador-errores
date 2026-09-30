package cl.casol.backend.catalogo.infrastructure.web.dto;

import cl.casol.backend.catalogo.domain.Hardware;

public record HardwareResponse(Integer id, String nombre, String sistemaOperativo) {
    public static HardwareResponse from(Hardware hardware) {
        return new HardwareResponse(hardware.id(), hardware.nombre(), hardware.sistemaOperativo());
    }
}
