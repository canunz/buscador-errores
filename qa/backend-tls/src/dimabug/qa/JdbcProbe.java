package dimabug.qa;

import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

/** Future separately authorized window only: one authenticated JDBC connection, SELECTs only. */
public class JdbcProbe {
    private static final String URL = "jdbc:mysql://127.0.0.1:13318/dima_bug_qa_mvp?serverTimezone=UTC&connectTimeout=3000&socketTimeout=8000&sslMode=VERIFY_CA&fallbackToSystemTrustStore=false&allowPublicKeyRetrieval=false&tlsVersions=TLSv1.2,TLSv1.3&socketFactory=dimabug.qa.PinnedSocketFactory";
    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException();
            Properties profile = new Properties();
            try (var input=Files.newInputStream(Path.of(args[0]))) { profile.load(input); }
            String url=profile.getProperty("spring.datasource.url");
            if (!URL.equals(url)) throw new IllegalArgumentException();
            String password=System.getenv("QA_DB_PASSWORD"),store=System.getenv("QA_TLS_TRUSTSTORE_URI");
            if(password==null || password.isBlank() || store==null || !store.equals(System.getProperty("qa.tls.storeUri"))) throw new IllegalArgumentException();
            Properties p=new Properties();p.setProperty("user","dimabug_qa_app");p.setProperty("password",password);
            p.setProperty("trustCertificateKeyStoreUrl",store);p.setProperty("trustCertificateKeyStoreType","JKS");
            p.setProperty("trustCertificateKeyStorePassword","qa-public-ca-integrity");
            Class.forName("com.mysql.cj.jdbc.Driver");
            try(Connection connection=DriverManager.getConnection(url,p)) {
                connection.setReadOnly(true);
                try(Statement s=connection.createStatement()) {
                    s.setQueryTimeout(5);
                    try(ResultSet r=s.executeQuery("SELECT DATABASE(),CURRENT_USER()")) {
                        if(!r.next() || !"dima_bug_qa_mvp".equals(r.getString(1)) || !"dimabug_qa_app@172.21.0.3".equals(r.getString(2)))throw new IllegalStateException();
                    }
                    try(ResultSet r=s.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                        if(!r.next() || r.getString(2)==null || r.getString(2).isBlank())throw new IllegalStateException();
                    }
                }
            }
            System.out.println("QA_AUTHENTICATED_JDBC_PROBE_OK");
        } catch(Exception e) {
            System.out.println("QA_AUTHENTICATED_JDBC_PROBE_FAILED");
            System.exit(1); // Never print JDBC exceptions, connection strings or credentials.
        }
    }
}
