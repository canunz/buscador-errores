package cl.casol.backend.shared.infrastructure.security;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;import java.util.*;
/** Límite local acotado. No confía en X-Forwarded-For aportado por el cliente. */
@Component @Order(-90)
public class LimiteAutoservicioFilter extends OncePerRequestFilter {
 private record Ventana(long inicio,int cantidad){}
 private final Map<String,Ventana> ventanas=new HashMap<>();
 private long inicioGlobal=System.nanoTime();private int global;
 private String ruta(HttpServletRequest r){String p=r.getServletPath();return p==null||p.isEmpty()?r.getRequestURI():p;}
 protected boolean shouldNotFilter(HttpServletRequest r){return !ruta(r).startsWith("/api/publico/");}
 private synchronized boolean admitir(String ip,boolean chat){
  long ahora=System.nanoTime(),minuto=60_000_000_000L;
  if(ahora-inicioGlobal>=minuto){inicioGlobal=ahora;global=0;ventanas.entrySet().removeIf(e->ahora-e.getValue().inicio()>=minuto);}
  if(global>=600)return false;
  String clave=ip+(chat?":chat":":lectura");var v=ventanas.get(clave);
  if(v==null||ahora-v.inicio()>=minuto){if(v==null&&ventanas.size()>=4096)return false;v=new Ventana(ahora,0);}
  if(v.cantidad()>=(chat?10:60))return false;
  ventanas.put(clave,new Ventana(v.inicio(),v.cantidad()+1));global++;return true;
 }
 protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException{
  s.setHeader("Cache-Control","no-store");s.setHeader("X-Content-Type-Options","nosniff");
  if(r.getContentLengthLong()>8192){s.setStatus(413);s.setContentType("application/json");s.getWriter().write("{\"message\":\"Solicitud demasiado grande\"}");return;}
  if(!admitir(r.getRemoteAddr(),ruta(r).equals("/api/publico/chatbot/consultas"))){s.setStatus(429);s.setHeader("Retry-After","60");s.setContentType("application/json");s.getWriter().write("{\"message\":\"Demasiadas consultas. Intenta en un minuto.\"}");return;}
  if("POST".equals(r.getMethod())){
   byte[] body=r.getInputStream().readNBytes(8193);
   if(body.length>8192){s.setStatus(413);s.setContentType("application/json");s.getWriter().write("{\"message\":\"Solicitud demasiado grande\"}");return;}
   var input=new ByteArrayInputStream(body);
   var wrapped=new HttpServletRequestWrapper(r){
    public int getContentLength(){return body.length;}
    public long getContentLengthLong(){return body.length;}
    public ServletInputStream getInputStream(){return new ServletInputStream(){
     public int read(){return input.read();}
     public int read(byte[] b,int off,int len){return input.read(b,off,len);}
     public boolean isFinished(){return input.available()==0;}
     public boolean isReady(){return true;}
     public void setReadListener(ReadListener listener){throw new IllegalStateException("Lectura síncrona");}
    };}
    public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
   };
   chain.doFilter(wrapped,s);return;
  }
  chain.doFilter(r,s);
 }
}
