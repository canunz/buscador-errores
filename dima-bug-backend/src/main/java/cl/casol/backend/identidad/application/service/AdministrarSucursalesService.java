package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.SucursalRepository;
import cl.casol.backend.identidad.domain.Sucursal;
import cl.casol.backend.identidad.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly=true)
public class AdministrarSucursalesService {
    private static final Pattern CORREO=Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+@dimarsa\\.cl",Pattern.CASE_INSENSITIVE);
    private final SucursalRepository sucursales;
    public AdministrarSucursalesService(SucursalRepository sucursales) { this.sucursales=sucursales; }

    public List<Sucursal> listar() { return sucursales.listar(); }
    public List<Sucursal> disponibles() { return listar().stream().filter(Sucursal::activa).toList(); }
    public Sucursal buscar(Integer id) {
        return sucursales.buscar(id).orElseThrow(()->new OrganizacionInvalidaException("La sucursal no existe"));
    }
    @Transactional
    public Sucursal crear(String codigo,String nombre,String responsable,String correo) {
        return crear(codigo,nombre,responsable,correo,java.util.List.of());
    }
    @Transactional public Sucursal crear(String codigo,String nombre,String responsable,String correo,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios){
        String c=codigo(codigo); unico(c,null);
        return sucursales.guardar(new Sucursal(null,c,texto(nombre,100,"Nombre"),
                texto(responsable,100,"Responsable"),correo(correo),true,null,destinatarios));
    }
    @Transactional
    public Sucursal editar(Integer id,String codigo,String nombre,String responsable,String correo,Long version) {
        return editar(id,codigo,nombre,responsable,correo,version,null);
    }
    @Transactional public Sucursal editar(Integer id,String codigo,String nombre,String responsable,String correo,Long version,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios){
        sucursales.bloquear(id); var actual=buscar(id); version(actual,version);
        String c=codigo(codigo); unico(c,id);
        return sucursales.guardar(new Sucursal(id,c,texto(nombre,100,"Nombre"),
                texto(responsable,100,"Responsable"),correo(correo),actual.activa(),actual.version(),destinatarios==null?actual.destinatarios():destinatarios,actual.ciudad(),actual.responsableCargo()));
    }
    @Transactional
    public Sucursal estado(Integer id,boolean activa,Long version) {
        sucursales.bloquear(id); var actual=buscar(id); version(actual,version);
        return actual.activa()==activa ? actual : sucursales.guardar(new Sucursal(actual.id(),actual.codigo(),
                actual.nombre(),actual.responsableNombre(),actual.responsableEmail(),activa,actual.version(),actual.destinatarios(),actual.ciudad(),actual.responsableCargo()));
    }
    @Transactional public Sucursal destinatarios(Integer id,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios,Long version){
        sucursales.bloquear(id);var actual=buscar(id);version(actual,version);
        return sucursales.guardar(new Sucursal(id,actual.codigo(),actual.nombre(),actual.responsableNombre(),actual.responsableEmail(),actual.activa(),actual.version(),destinatarios,actual.ciudad(),actual.responsableCargo()));
    }
    @Transactional public Sucursal crearConUbicacion(String codigo,String nombre,String responsable,String correo,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios,String ciudad,String cargo){var s=crear(codigo,nombre,responsable,correo,destinatarios);return ubicacion(s.id(),ciudad,cargo,s.version());}
    @Transactional public Sucursal editarConUbicacion(Integer id,String codigo,String nombre,String responsable,String correo,Long version,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios,String ciudad,String cargo){var s=editar(id,codigo,nombre,responsable,correo,version,destinatarios);return ubicacion(id,ciudad,cargo,s.version());}
    @Transactional public Sucursal ubicacion(Integer id,String ciudad,String cargo,Long version){
        sucursales.bloquear(id);var a=buscar(id);version(a,version);
        return sucursales.guardar(new Sucursal(a.id(),a.codigo(),a.nombre(),a.responsableNombre(),a.responsableEmail(),a.activa(),a.version(),a.destinatarios(),opcional(ciudad),opcional(cargo)));
    }
    private String opcional(String valor){return valor==null || valor.isBlank()?null:texto(valor,100,"Ciudad o cargo");}
    private void version(Sucursal s,Long v) {
        if(v==null || v<0) throw new OrganizacionInvalidaException("Versión obligatoria");
        if(!v.equals(s.version())) throw new OrganizacionConflictoException("La sucursal cambió. Recarga y revisa antes de guardar");
    }
    private void unico(String c,Integer id) {
        if(sucursales.buscarPorCodigo(c).filter(s->!Objects.equals(s.id(),id)).isPresent())
            throw new OrganizacionConflictoException("El código de sucursal ya existe, incluso si está inactiva");
    }
    private String codigo(String c) {
        String normalizado=texto(c,30,"Código").toUpperCase(Locale.ROOT);
        if(!normalizado.matches("[A-Z0-9][A-Z0-9_-]{0,29}")) throw new OrganizacionInvalidaException("Código: usa letras sin acentos, números, guion o guion bajo");
        return normalizado;
    }
    private String texto(String t,int max,String campo) {
        if(t==null || t.isBlank() || t.length()>max || t.codePoints().anyMatch(Character::isISOControl))
            throw new OrganizacionInvalidaException(campo+" obligatorio; máximo "+max+" caracteres, sin controles");
        return t.strip();
    }
    private String correo(String c) {
        String email=texto(c,120,"Correo del responsable");
        String local=email.split("@",-1)[0];
        if(!CORREO.matcher(email).matches() || local.length()>64 || local.startsWith(".") || local.endsWith(".") || local.contains(".."))
            throw new OrganizacionInvalidaException("Usa un único correo del responsable con dominio @dimarsa.cl");
        int arroba=email.indexOf('@');
        return email.substring(0,arroba)+"@dimarsa.cl";
    }
}
