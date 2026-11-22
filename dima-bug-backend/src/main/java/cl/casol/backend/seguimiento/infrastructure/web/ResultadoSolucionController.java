package cl.casol.backend.seguimiento.infrastructure.web;

import cl.casol.backend.seguimiento.application.service.RegistrarEfectividadSolucionService;
import cl.casol.backend.seguimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Controlador REST de resultado solución. Atiende las rutas que empiezan con /api/conocimientos/{conocimientoId}/soluciones/{solucionId}.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/soluciones/{solucionId}")
public class ResultadoSolucionController {
    private final RegistrarEfectividadSolucionService service;
    public ResultadoSolucionController(RegistrarEfectividadSolucionService service){this.service=service;}
    // POST /api/conocimientos/{conocimientoId}/soluciones/{solucionId}/resultados: registrar.
    @PostMapping("/resultados") @ResponseStatus(HttpStatus.CREATED)
    public ResultadoSolucionResponse registrar(@PathVariable Integer conocimientoId,@PathVariable Integer solucionId,
            @Valid @RequestBody RegistrarResultadoRequest request,Authentication authentication){
        return ResultadoSolucionResponse.from(service.registrar(conocimientoId,solucionId,request.funciono(),
                request.comentario(),authentication.getName()));}
    // GET /api/conocimientos/{conocimientoId}/soluciones/{solucionId}/resultados: listar.
    @GetMapping("/resultados")
    public List<ResultadoSolucionResponse> listar(@PathVariable Integer conocimientoId,@PathVariable Integer solucionId){
        return service.listar(conocimientoId,solucionId).stream().map(ResultadoSolucionResponse::from).toList();}
    // GET /api/conocimientos/{conocimientoId}/soluciones/{solucionId}/efectividad: efectividad.
    @GetMapping("/efectividad")
    public EfectividadSolucionResponse efectividad(@PathVariable Integer conocimientoId,@PathVariable Integer solucionId){
        return EfectividadSolucionResponse.from(service.obtenerEfectividad(conocimientoId,solucionId));}
}
