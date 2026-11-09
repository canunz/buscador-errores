package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.shared.application.archivo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.net.URI;
@Service @Transactional(readOnly=true)
public class AutoservicioPublicoService {
 private final SolucionRepository soluciones;private final ConocimientoRepository conocimientos;
 private final MaterialSolucionRepository materiales;private final ArchivoMaterialService archivos;
 public AutoservicioPublicoService(SolucionRepository s,ConocimientoRepository c,MaterialSolucionRepository m,ArchivoMaterialService a){soluciones=s;conocimientos=c;materiales=m;archivos=a;}
 public List<Solucion> buscar(String q){return soluciones.buscarPublicas(q==null?"":q.strip(),50);}
 public Solucion publica(Integer id){var s=solucion(id);if(!s.activo()||!s.autoservicio()||conocimientos.buscarPorId(s.conocimientoId()).filter(c->c.getEstado()==EstadoConocimiento.PUBLICADO).isEmpty())throw new SolucionNoEncontradaException(id);return s;}
 public Solucion solucion(Integer id){return soluciones.buscarPorId(id).orElseThrow(()->new SolucionNoEncontradaException(id));}
 public List<MaterialSolucion> materiales(Integer id,boolean publico){if(publico)publica(id);else solucion(id);return materiales.listar(id).stream().filter(m->!publico||m.publicado()).toList();}
 private MaterialSolucion material(Integer s,Integer id,boolean publico){if(publico)publica(s);else solucion(s);return materiales.buscar(id).filter(m->m.solucionId().equals(s)&&(!publico||m.publicado())).orElseThrow(()->new MaterialApoyoNoEncontradoException(id));}
 public ArchivoDescarga descargar(Integer s,Integer id,boolean publico){return archivos.descargar(material(s,id,publico).archivo());}
 @Transactional public MaterialSolucion crear(Integer s,String nombre,TipoMaterial tipo,String url,boolean publicado){editable(s);validarNombre(nombre);validarUrl(url);limite(s);return materiales.guardar(new MaterialSolucion(null,s,nombre.strip(),tipo,url.strip(),publicado));}
 public MaterialSolucion subir(Integer s,String nombre,TipoMaterial tipo,ArchivoSubido archivo,boolean publicado){editable(s);final MaterialSolucion[] resultado=new MaterialSolucion[1];archivos.crear(nombre,tipo,archivo,ref->{editable(s);limite(s);var m=materiales.guardar(new MaterialSolucion(null,s,nombre.strip(),tipo,ref,publicado));resultado[0]=m;return m.archivo();});return resultado[0];}
 @Transactional public MaterialSolucion publicarMaterial(Integer s,Integer id,boolean publicado){editable(s);var m=material(s,id,false);if(publicado&&!m.url().startsWith(FormatoArchivo.PREFIJO))validarUrl(m.url());return materiales.guardar(new MaterialSolucion(m.id(),s,m.nombre(),m.tipo(),m.url(),publicado));}
 public void eliminar(Integer s,Integer id){editable(s);archivos.eliminar(()->material(s,id,false).archivo(),m->{editable(s);materiales.eliminar(m.id());});}
 private void editable(Integer id){if(!solucion(id).activo())throw new SolucionInactivaException();}
 private void limite(Integer s){if(materiales.listar(s).size()>=50)throw new ArchivoException(ArchivoException.Motivo.INVALIDO,"Máximo 50 materiales por solución");}
 private void validarNombre(String s){if(s==null||s.isBlank()||s.length()>150)throw new ArchivoException(ArchivoException.Motivo.INVALIDO,"Título obligatorio; máximo 150 caracteres");}
 public static void validarUrl(String s){try{var u=URI.create(s);String h=u.getHost();if(s.length()>500||!Set.of("https","http").contains(u.getScheme())||u.getUserInfo()!=null||h==null)throw new IllegalArgumentException();h=h.toLowerCase(Locale.ROOT);if(h.equals("localhost")||h.endsWith(".local")||h.endsWith(".internal")||h.contains(":")||h.matches("(?:127|10|0)\\..*")||h.matches("192\\.168\\..*")||h.matches("172\\.(?:1[6-9]|2[0-9]|3[01])\\..*")||h.startsWith("169.254."))throw new IllegalArgumentException();}catch(RuntimeException e){throw new ArchivoException(ArchivoException.Motivo.INVALIDO,"Usa un enlace externo HTTP/HTTPS válido, sin credenciales ni direcciones internas");}}
}
