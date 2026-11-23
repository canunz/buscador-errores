import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

/** Pasarela QA sin credenciales: solo CONNECT graph.facebook.com:443, TLS extremo a extremo. */
public final class GraphHttpsProxy {
    private static final Semaphore SLOTS=new Semaphore(4);
    /** Graph API y el CDN de medios de WhatsApp (lookaside.fbsbx.com), necesario para descargar fotos para OCR. */
    static final java.util.Set<String> DESTINOS=java.util.Set.of("graph.facebook.com","lookaside.fbsbx.com");
    static String destino(String line){
        for(String host:DESTINOS)if(("CONNECT "+host+":443 HTTP/1.1").equals(line))return host;
        return null;
    }
    static boolean allowed(String line){return destino(line)!=null;}
    static String header(InputStream in)throws IOException{
        var bytes=new ByteArrayOutputStream();int end=0;
        while(bytes.size()<8192){int c=in.read();if(c<0)throw new EOFException();bytes.write(c);
            end=(end<<8)|c;if(end==0x0d0a0d0a)return bytes.toString(StandardCharsets.US_ASCII);}
        throw new IOException("HEADER_LIMIT");
    }
    static void pipe(InputStream in,OutputStream out){try{in.transferTo(out);out.flush();}catch(IOException ignored){}}
    static void handle(Socket client){
        try(client){
            client.setSoTimeout(30000);
            String request=header(client.getInputStream());String line=request.substring(0,request.indexOf("\r\n"));
            if(!allowed(line)){client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));return;}
            try(var upstream=new Socket()){
                upstream.connect(new InetSocketAddress(destino(line),443),10000);upstream.setSoTimeout(30000);
                client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));client.getOutputStream().flush();
                Thread upload=Thread.startVirtualThread(()->{try{pipe(client.getInputStream(),upstream.getOutputStream());}catch(IOException ignored){}});
                pipe(upstream.getInputStream(),client.getOutputStream());
                upstream.close();client.close();upload.join(1000);
            }
        }catch(IOException ignored){}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{SLOTS.release();}
    }
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("--self-test")){
            var proxies=ProxySelector.getDefault().select(URI.create("https://graph.facebook.com/v27.0/"));
            if(proxies.getFirst().type()!=Proxy.Type.HTTP||!((InetSocketAddress)proxies.getFirst().address()).getHostString().equals("qa-proxy"))throw new AssertionError("HTTPS_PROXY_NOT_SELECTED");
            if(!allowed("CONNECT graph.facebook.com:443 HTTP/1.1")||!allowed("CONNECT lookaside.fbsbx.com:443 HTTP/1.1"))throw new AssertionError();
            for(String invalid:new String[]{"CONNECT lookaside.fbsbx.com:80 HTTP/1.1","CONNECT evil.fbsbx.com:443 HTTP/1.1","CONNECT lookaside.fbsbx.com.evil:443 HTTP/1.1"})if(allowed(invalid))throw new AssertionError();
            for(String invalid:new String[]{"CONNECT evil.example:443 HTTP/1.1","CONNECT graph.facebook.com:80 HTTP/1.1","CONNECT graph.facebook.com.evil:443 HTTP/1.1","CONNECT 127.0.0.1:443 HTTP/1.1","GET https://graph.facebook.com/ HTTP/1.1","CONNECT graph.facebook.com:443@evil HTTP/1.1"})if(allowed(invalid))throw new AssertionError();
            if(!header(new ByteArrayInputStream("CONNECT graph.facebook.com:443 HTTP/1.1\r\nHost: graph.facebook.com:443\r\n\r\n".getBytes(StandardCharsets.US_ASCII))).endsWith("\r\n\r\n"))throw new AssertionError();
            try{header(new ByteArrayInputStream("x".repeat(8192).getBytes(StandardCharsets.US_ASCII)));throw new AssertionError();}catch(IOException expected){}
            System.out.println("PROXY_POLICY_PASS");return;
        }
        String bind="0.0.0.0";
        if(args.length==1&&args[0].equals("--bind=127.0.0.1"))bind="127.0.0.1";
        else if(args.length!=0)throw new IllegalArgumentException("BIND_NOT_ALLOWED");
        try(var server=new ServerSocket(18888,8,InetAddress.getByName(bind))){
            while(true){Socket client=server.accept();if(!SLOTS.tryAcquire()){client.close();continue;}Thread.startVirtualThread(()->handle(client));}
        }
    }
}
