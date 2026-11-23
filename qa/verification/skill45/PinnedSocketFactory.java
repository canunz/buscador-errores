package dimabug.qa;

import com.mysql.cj.conf.PropertySet;
import com.mysql.cj.conf.PropertyDefinitions.SslMode;
import com.mysql.cj.log.Log;
import com.mysql.cj.protocol.ServerSession;
import com.mysql.cj.protocol.SocketConnection;
import com.mysql.cj.protocol.StandardSocketFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/** QA only: CA validation by Connector/J, then pinned identity BEFORE authentication. */
public class PinnedSocketFactory extends StandardSocketFactory {
    public static final String PIN = System.getenv("SKILL45_PIN");
    public static void check(boolean ok, String code) throws IOException {
        if (!ok) throw new IOException(code);
    }
    public static void validateProperties(String host, int port, PropertySet p) throws IOException {
        check("127.0.0.1".equals(host) && port == Integer.parseInt(System.getenv("SKILL45_PORT")), "QA_ENDPOINT_REJECTED");
        check(p.<SslMode>getEnumProperty("sslMode").getValue() == SslMode.VERIFY_CA, "QA_CA_REQUIRED");
        check(Boolean.FALSE.equals(p.getBooleanProperty("fallbackToSystemTrustStore").getValue()), "QA_FALLBACK_REJECTED");
        check(Boolean.FALSE.equals(p.getBooleanProperty("allowPublicKeyRetrieval").getValue()), "QA_KEY_RETRIEVAL_REJECTED");
        check("TLSv1.2,TLSv1.3".equals(p.getStringProperty("tlsVersions").getValue()), "QA_TLS_VERSIONS_REJECTED");
        check("JKS".equals(p.getStringProperty("trustCertificateKeyStoreType").getValue()), "QA_STORE_TYPE_REJECTED");
        check("qa-public-ca-integrity".equals(p.getStringProperty("trustCertificateKeyStorePassword").getValue()), "QA_STORE_INTEGRITY_REJECTED");
        try {
            String configured = p.getStringProperty("trustCertificateKeyStoreUrl").getValue();
            check(configured != null && configured.equals(System.getenv("SKILL45_STORE_URI")), "QA_STORE_URI_REJECTED");
            URI uri = URI.create(configured);
            check("file".equals(uri.getScheme()), "QA_LOCAL_STORE_REQUIRED");
            Path file = Path.of(uri);
            check(!Files.isSymbolicLink(file) && Files.isRegularFile(file), "QA_STORE_FILE_REJECTED");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
            check(digest.equals(System.getenv("SKILL45_STORE_SHA")), "QA_STORE_HASH_REJECTED");
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("QA_STORE_REJECTED"); }
    }
    @Override public <T extends Closeable> T connect(String host, int port, PropertySet p, int timeout) throws IOException {
        validateProperties(host, port, p);
        return super.connect(host, port, p, timeout);
    }
    public static void verifySession(SSLSession session) throws IOException {
        try {
            check(session.isValid(), "QA_TLS_SESSION_INVALID");
            check("TLSv1.2".equals(session.getProtocol()) || "TLSv1.3".equals(session.getProtocol()), "QA_TLS_PROTOCOL_REJECTED");
            byte[] der = session.getPeerCertificates()[0].getEncoded();
            check(matchesPin(der, PIN), "QA_CERT_PIN_MISMATCH");
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("QA_PEER_CERT_REJECTED"); }
    }
    public static boolean matchesPin(byte[] der, String expected) throws Exception {
        return MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(der), HexFormat.of().parseHex(expected));
    }
    protected <T extends Closeable> T negotiate(SocketConnection c, ServerSession s, Log log) throws IOException {
        return super.performTlsHandshake(c, s, log);
    }
    @Override public <T extends Closeable> T performTlsHandshake(SocketConnection c, ServerSession s) throws IOException {
        return performTlsHandshake(c, s, null);
    }
    @Override @SuppressWarnings("unchecked")
    public <T extends Closeable> T performTlsHandshake(SocketConnection c, ServerSession s, Log log) throws IOException {
        try {
            validateProperties(host, port, c.getPropertySet());
            Closeable result = negotiate(c, s, log); // Mandatory CA/expiry validation in Connector/J.
            check(result instanceof SSLSocket, "QA_TLS_SOCKET_REQUIRED");
            verifySession(((SSLSocket)result).getSession());
            System.out.println("QA_JDBC_TLS_CA_PIN_VERIFIED"); // No identity, URL, credentials or certificate content.
            return (T) result; // Only now may Connector/J send authentication.
        } catch (IOException | RuntimeException e) {
            closeQuietly(sslSocket); closeQuietly(rawSocket);
            throw new IOException("QA_TLS_HANDSHAKE_REJECTED");
        }
    }
    private static void closeQuietly(Socket socket) { try { if (socket != null) socket.close(); } catch (IOException ignored) {} }
}
