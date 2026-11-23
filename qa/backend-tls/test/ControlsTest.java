package dimabug.qa;

import com.mysql.cj.conf.DefaultPropertySet;
import com.mysql.cj.conf.PropertySet;
import com.mysql.cj.protocol.SocketConnection;
import com.mysql.cj.protocol.ServerSession;
import com.mysql.cj.log.Log;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

/** Public certificates only; no network, SQL, DB passwords or Spring. */
public class ControlsTest {
    static int passed;
    interface Checked { void run() throws Exception; }
    static void ok(boolean value) { if (!value) throw new AssertionError("QA_TEST_FAILED"); }
    static void rejects(Checked action) throws Exception {
        try { action.run(); } catch (IOException | GeneralSecurityException expected) { passed++; return; }
        throw new AssertionError("QA_EXPECTED_REJECTION");
    }
    static PropertySet props(Path store, String changed, String value) throws Exception {
        Properties p = new Properties();
        p.setProperty("sslMode","VERIFY_CA"); p.setProperty("fallbackToSystemTrustStore","false");
        p.setProperty("allowPublicKeyRetrieval","false");p.setProperty("tlsVersions","TLSv1.2,TLSv1.3");
        p.setProperty("trustCertificateKeyStoreType","JKS");p.setProperty("trustCertificateKeyStorePassword","qa-public-ca-integrity");
        p.setProperty("trustCertificateKeyStoreUrl",store.toUri().toString());
        if (changed != null) p.setProperty(changed,value);
        DefaultPropertySet result = new DefaultPropertySet(); result.initializeProperties(p); return result;
    }
    static X509Certificate certificate(Path file) throws Exception {
        try(var input=Files.newInputStream(file)) { return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input); }
    }
    static SSLSession session(X509Certificate leaf, String protocol, boolean valid) {
        return (SSLSession)Proxy.newProxyInstance(ControlsTest.class.getClassLoader(),new Class<?>[]{SSLSession.class},(o,m,a)->switch(m.getName()) {
            case "isValid" -> valid;
            case "getProtocol" -> protocol;
            case "getPeerCertificates" -> new java.security.cert.Certificate[]{leaf};
            default -> throw new UnsupportedOperationException();
        });
    }
    static class FakeSocket extends SSLSocket {
        final SSLSession session; boolean closed;
        FakeSocket(SSLSession s) {session=s;}
        public SSLSession getSession(){return session;}
        public void close(){closed=true;}
        public String[] getSupportedCipherSuites(){return new String[0];}
        public String[] getEnabledCipherSuites(){return new String[0];}
        public void setEnabledCipherSuites(String[] s){}
        public String[] getSupportedProtocols(){return new String[0];}
        public String[] getEnabledProtocols(){return new String[0];}
        public void setEnabledProtocols(String[] s){}
        public void addHandshakeCompletedListener(HandshakeCompletedListener l){}
        public void removeHandshakeCompletedListener(HandshakeCompletedListener l){}
        public void startHandshake(){}
        public void setUseClientMode(boolean b){}
        public boolean getUseClientMode(){return true;}
        public void setNeedClientAuth(boolean b){}
        public boolean getNeedClientAuth(){return false;}
        public void setWantClientAuth(boolean b){}
        public boolean getWantClientAuth(){return false;}
        public void setEnableSessionCreation(boolean b){}
        public boolean getEnableSessionCreation(){return false;}
    }
    static class Factory extends PinnedSocketFactory {
        final FakeSocket fake; final boolean failCa; boolean negotiated;
        Factory(FakeSocket f, boolean fail) {fake=f;failCa=fail;host="127.0.0.1";port=13318;rawSocket=f;sslSocket=f;}
        @Override @SuppressWarnings("unchecked") protected <T extends Closeable> T negotiate(SocketConnection c,ServerSession s,Log l) throws IOException {
            negotiated=true;
            if(failCa)throw new IOException("SYNTHETIC_CA_FAILURE");
            return (T)fake;
        }
    }
    static SocketConnection connection(PropertySet p) {
        return (SocketConnection)Proxy.newProxyInstance(ControlsTest.class.getClassLoader(),new Class<?>[]{SocketConnection.class},(o,m,a)->{
            if(m.getName().equals("getPropertySet"))return p;
            throw new UnsupportedOperationException();
        });
    }
    public static void main(String[] args) throws Exception {
        Path store=Path.of(args[0]); X509Certificate leaf=certificate(Path.of(args[1])),ca=certificate(Path.of(args[2]));
        System.setProperty("qa.tls.storeUri",store.toUri().toString());
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(store)));
        System.setProperty("qa.tls.storeSha256",hash);
        PropertySet p=props(store,null,null);
        PinnedSocketFactory.validateProperties("127.0.0.1",13318,p);passed++;
        rejects(()->PinnedSocketFactory.validateProperties("localhost",13318,p));
        rejects(()->PinnedSocketFactory.validateProperties("127.0.0.1",3306,p));
        for(String[] mutation:new String[][]{{"sslMode","PREFERRED"},{"sslMode","REQUIRED"},{"fallbackToSystemTrustStore","true"},
                {"allowPublicKeyRetrieval","true"},{"tlsVersions","TLSv1.2"},{"trustCertificateKeyStoreType","PKCS12"},
                {"trustCertificateKeyStorePassword","wrong-public-value"}}) {
            rejects(()->PinnedSocketFactory.validateProperties("127.0.0.1",13318,props(store,mutation[0],mutation[1])));
        }
        System.setProperty("qa.tls.storeSha256","0".repeat(64));
        rejects(()->PinnedSocketFactory.validateProperties("127.0.0.1",13318,p));
        System.setProperty("qa.tls.storeSha256",hash);
        PinnedSocketFactory.verifySession(session(leaf,"TLSv1.3",true));passed++;
        PinnedSocketFactory.verifySession(session(leaf,"TLSv1.2",true));passed++;
        rejects(()->PinnedSocketFactory.verifySession(session(leaf,"TLSv1.1",true)));
        rejects(()->PinnedSocketFactory.verifySession(session(leaf,"TLSv1.3",false)));
        rejects(()->PinnedSocketFactory.verifySession(session(ca,"TLSv1.3",true)));
        rejects(()->leaf.checkValidity(new Date(leaf.getNotAfter().getTime()+1000)));
        var path=CertificateFactory.getInstance("X.509").generateCertPath(List.of(leaf));
        var params=new PKIXParameters(Set.of(new TrustAnchor(ca,null)));params.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(path,params);passed++;
        var wrongParams=new PKIXParameters(Set.of(new TrustAnchor(leaf,null)));wrongParams.setRevocationEnabled(false);
        var caPath=CertificateFactory.getInstance("X.509").generateCertPath(List.of(ca));
        rejects(()->CertPathValidator.getInstance("PKIX").validate(caPath,wrongParams));
        FakeSocket socket=new FakeSocket(session(leaf,"TLSv1.3",true));Factory factory=new Factory(socket,false);
        ok(factory.performTlsHandshake(connection(p),null,null)==socket && !socket.closed);passed++;
        FakeSocket bad=new FakeSocket(session(ca,"TLSv1.3",true));Factory badFactory=new Factory(bad,false);
        rejects(()->badFactory.performTlsHandshake(connection(p),null,null));ok(bad.closed);passed++;
        FakeSocket caFail=new FakeSocket(session(leaf,"TLSv1.3",true));Factory failFactory=new Factory(caFail,true);
        rejects(()->failFactory.performTlsHandshake(connection(p),null,null));ok(caFail.closed);passed++;
        FakeSocket downgrade=new FakeSocket(session(leaf,"TLSv1.3",true));Factory downgradeFactory=new Factory(downgrade,false);
        rejects(()->downgradeFactory.performTlsHandshake(connection(props(store,"sslMode","PREFERRED")),null,null));
        ok(!downgradeFactory.negotiated && downgrade.closed);passed++;
        System.out.println("QA_JDBC_CONTROLS_PASSED="+passed);
    }
}
