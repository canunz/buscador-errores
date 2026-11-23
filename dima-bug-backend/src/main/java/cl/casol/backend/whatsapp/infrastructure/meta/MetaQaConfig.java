package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.infrastructure.http.*;
import cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import java.nio.file.Path;

@Configuration(proxyBeanMethods=false)
@Profile("qa-whatsapp-meta & !staging & !prod & !production")
public class MetaQaConfig {
    @Bean ObservabilidadMetaQa observabilidadMetaQa(){return new ObservabilidadMetaQa();}
    @Bean ControlesMetaQa controlesMetaQa(ObservabilidadMetaQa o,
            @Value("${dimabug.whatsapp.meta-qa.recepcion:false}")boolean r,
            @Value("${dimabug.whatsapp.meta-qa.procesamiento:false}")boolean p,
            @Value("${dimabug.whatsapp.meta-qa.medios:false}")boolean m,
            @Value("${dimabug.whatsapp.meta-qa.envio:false}")boolean e){return new ControlesMetaQa(new ControlesMetaQa.Estado(r,p,m,e),o);}
    @Bean PoliticaMetaQa politicaMetaQa(@Value("${dimabug.whatsapp.meta-qa.waba}")String w,
            @Value("${dimabug.whatsapp.meta-qa.numero}")String n,
            @Value("${dimabug.whatsapp.meta-qa.participantes}")String p){return new PoliticaMetaQa(w,n,p);}
    @Bean ValidacionWebhookLocal firmaMetaQa(@Value("${dimabug.whatsapp.meta-qa.verify-token}")String token,
            @Value("${dimabug.whatsapp.meta-qa.app-secret}")String secreto){return new ValidacionWebhookLocal(token,secreto,EntradaMetaQaAdapter.MAX_BYTES);}
    @Bean EntradaMetaQaAdapter entradaMetaQa(AlmacenWhatsAppPort almacen,ValidacionWebhookLocal firma,
            PoliticaMetaQa politica,ControlesMetaQa controles,ObservabilidadMetaQa o){return new EntradaMetaQaAdapter(almacen,firma,politica,controles,o);}
    /** Credencial Graph: solo existe si se autorizó conexion-meta (descarga de fotos) o envio-real (respuestas). */
    @Bean @ConditionalOnExpression("${dimabug.whatsapp.meta-qa.conexion-meta:false} or ${dimabug.whatsapp.meta-qa.envio-real:false}")
    GraphMetaCliente graphMetaCliente(@Value("${dimabug.whatsapp.meta-qa.graph-version:v23.0}")String version,
            @Value("${dimabug.whatsapp.meta-qa.graph-token:}")String token){return new GraphMetaCliente(version,token);}
    /** Sin conexion-meta: solo fixtures locales. Con conexion-meta: descarga la foto real desde Meta y la lee con OCR local. */
    @Bean(name="ocrWebhookHttpLocal") ReconocerImagenPort ocrMetaQa(@Value("${dimabug.whatsapp.qa.raiz}")String raiz,
            @Value("${dimabug.whatsapp.ocr.node:}")String node,@Value("${dimabug.whatsapp.ocr.tmp:}")String tmp,
            @Value("${dimabug.whatsapp.meta-qa.conexion-meta:false}")boolean conexion,ObjectProvider<GraphMetaCliente> graph,PoliticaMetaQa politica){
        // Sin node/tmp configurados se conservan las rutas QA de Windows; en el servidor Linux se declaran ambas.
        var ocr=new OcrNodeProcesoAdapter(Path.of(raiz),node.isBlank()?null:Path.of(node),tmp.isBlank()?null:Path.of(tmp),java.time.Duration.ofSeconds(35));
        return conexion?new ReconocerImagenMetaAdapter(ocr,graph.getObject(),politica):ocr;
    }
    /** Envío real de respuestas (solo a participantes de la lista QA). Sin este bean la salida queda SIMULADA. */
    @Bean @ConditionalOnProperty(name="dimabug.whatsapp.meta-qa.envio-real",havingValue="true")
    EnviarMensajeCanalPort envioMetaGraph(GraphMetaCliente cliente,PoliticaMetaQa politica){return new EnvioMetaGraphAdapter(cliente,politica);}
    @Bean @Order(1) SecurityFilterChain seguridadMetaQa(HttpSecurity http)throws Exception{return WebhookHttpLocalConfig.seguridadLoopback(http);}
}
