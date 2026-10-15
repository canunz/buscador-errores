package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service public class ReindexarPruebaService {
 private final PruebaAdministrativaRepository pruebas;private final ConocimientoRepository conocimientos;private final IndexarConocimientoService index;
 public ReindexarPruebaService(PruebaAdministrativaRepository pruebas,ConocimientoRepository conocimientos,IndexarConocimientoService index){this.pruebas=pruebas;this.conocimientos=conocimientos;this.index=index;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void actualizar(Integer id){for(var c:pruebas.conocimientos(id))if(conocimientos.buscarPorId(c).isPresent())index.indexar(c);}
}