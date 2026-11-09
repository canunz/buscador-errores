package cl.casol.backend.correo.infrastructure.gmail;

import cl.casol.backend.correo.application.port.out.GoogleGmailPort;
import cl.casol.backend.correo.application.service.GmailException;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import static cl.casol.backend.correo.application.service.GmailException.Tipo.*;

@Component
public class GoogleGmailHttp implements GoogleGmailPort {
    public static final String AUTH="https://accounts.google.com/o/oauth2/v2/auth",TOKEN="https://oauth2.googleapis.com/token",REVOKE="https://oauth2.googleapis.com/revoke",SEND="https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
    private final GmailConfig config;private final Clock reloj;
    private final RestClient api;
    private final RestClientAuthorizationCodeTokenResponseClient codigos=new RestClientAuthorizationCodeTokenResponseClient();
    private final RestClientRefreshTokenTokenResponseClient refrescos=new RestClientRefreshTokenTokenResponseClient();
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleGmailHttp(GmailConfig config,Clock reloj){this(config,reloj,conTimeout(),conTimeout());}
    /** Builders inyectables exclusivamente para pruebas HTTP sin red. */
    public GoogleGmailHttp(GmailConfig config,Clock reloj,RestClient.Builder apiBuilder,RestClient.Builder oauthBuilder){
        this.config=config;this.reloj=reloj;api=apiBuilder.build();
        var oauth=oauthBuilder.messageConverters(c->{c.clear();c.add(new FormHttpMessageConverter());c.add(new OAuth2AccessTokenResponseHttpMessageConverter());}).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();
        codigos.setRestClient(oauth);refrescos.setRestClient(oauth);
    }
    private static RestClient.Builder conTimeout(){var cliente=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();var factory=new JdkClientHttpRequestFactory(cliente);factory.setReadTimeout(Duration.ofSeconds(8));return RestClient.builder().requestFactory(factory);}
    private ClientRegistration registro(){
        if(!config.configurado())throw new GmailException(CONFIGURACION);
        return ClientRegistration.withRegistrationId("google").clientId(config.clientId).clientSecret(config.clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationUri(AUTH).tokenUri(TOKEN).redirectUri(config.callback).scope(GmailConfig.SCOPE).build();
    }
    private OAuth2AuthorizationRequest solicitud(String estado,String verificador){
        var r=registro();return OAuth2AuthorizationRequest.authorizationCode().authorizationUri(AUTH).clientId(r.getClientId()).redirectUri(r.getRedirectUri()).scopes(Set.of(GmailConfig.SCOPE)).state(estado)
            .attributes(a->a.put(PkceParameterNames.CODE_VERIFIER,verificador))
            .additionalParameters(a->{a.put("access_type","offline");a.put("prompt","consent select_account");a.put("include_granted_scopes","false");
                String cuenta=config.remitente==null?"":config.remitente.strip().toLowerCase();
                if(cuenta.endsWith("@dimarsa.cl"))a.put("hd","dimarsa.cl");
                a.put("login_hint",config.remitente);a.put(PkceParameterNames.CODE_CHALLENGE,pkce(verificador));a.put(PkceParameterNames.CODE_CHALLENGE_METHOD,"S256");}).build();
    }
    private String pkce(String valor){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(valor.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}catch(Exception ex){throw new GmailException(CONFIGURACION);}}
    public String autorizacion(String estado,String verificador){return solicitud(estado,verificador).getAuthorizationRequestUri();}
    private Tokens tokens(OAuth2AccessTokenResponse r){return new Tokens(r.getAccessToken().getTokenValue(),r.getRefreshToken()==null?null:r.getRefreshToken().getTokenValue(),r.getAccessToken().getExpiresAt(),r.getAccessToken().getScopes());}
    private GmailException errorToken(OAuth2AuthorizationException ex){
        String codigo=ex.getError().getErrorCode();
        if("invalid_client".equals(codigo))return new GmailException(CONFIGURACION);
        return new GmailException(Set.of("invalid_grant","invalid_token","invalid_scope","access_denied","unauthorized_client").contains(codigo)?AUTORIZACION:CONEXION);
    }
    public Tokens intercambiar(String codigo,String estado,String verificador){
        try{var respuesta=OAuth2AuthorizationResponse.success(codigo).state(estado).redirectUri(config.callback).build();return tokens(codigos.getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(registro(),new OAuth2AuthorizationExchange(solicitud(estado,verificador),respuesta))));}
        catch(OAuth2AuthorizationException ex){throw errorToken(ex);}catch(RestClientException ex){throw new GmailException(CONEXION);}
    }
    public Tokens renovar(String refresco){
        try{var acceso=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"no-utilizado-para-refrescar",reloj.instant().minusSeconds(3600),reloj.instant().minusSeconds(1),Set.of(GmailConfig.SCOPE));return tokens(refrescos.getTokenResponse(new OAuth2RefreshTokenGrantRequest(registro(),acceso,new OAuth2RefreshToken(refresco,reloj.instant()),Set.of(GmailConfig.SCOPE))));}
        catch(OAuth2AuthorizationException ex){throw errorToken(ex);}catch(RestClientException ex){throw new GmailException(CONEXION);}
    }
    public void revocar(String token){
        try{var form=new LinkedMultiValueMap<String,String>();form.add("token",token);api.post().uri(REVOKE).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();}
        catch(RestClientException ex){throw new GmailException(CONEXION);}
    }
    public String enviar(String acceso,String raw){
        try{var respuesta=api.post().uri(SEND).headers(h->h.setBearerAuth(acceso)).contentType(MediaType.APPLICATION_JSON).body(Map.of("raw",raw)).retrieve().body(Map.class);
            Object id=respuesta==null?null:respuesta.get("id");if(!(id instanceof String valor) || !valor.matches("[A-Za-z0-9_-]{1,64}"))throw new GmailException(ENVIO);return "GMAIL-"+valor;
        }catch(HttpClientErrorException.Unauthorized ex){throw new GmailException(AUTORIZACION);}catch(HttpClientErrorException ex){throw new GmailException(RECHAZO);}catch(RestClientException ex){throw new GmailException(ENVIO);}
    }
}
