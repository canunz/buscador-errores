package dimabug.qa;
import com.mysql.cj.conf.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.io.IOException;

/** Sin red, SQL ni secretos reales. */
public class SharedMysqlPinTest {
    interface Checked {void run()throws Exception;}
    static int passed=0;
    static void rejects(Checked r)throws Exception{try{r.run();throw new AssertionError();}catch(IOException expected){passed++;}}
    static PropertySet props(Path file,String key,String value)throws Exception{
        var p=new Properties();p.setProperty("sslMode","VERIFY_CA");p.setProperty("fallbackToSystemTrustStore","false");
        p.setProperty("allowPublicKeyRetrieval","false");p.setProperty("tlsVersions","TLSv1.2,TLSv1.3");
        p.setProperty("trustCertificateKeyStoreType","PKCS12");p.setProperty("trustCertificateKeyStorePassword","qa-public-ca-integrity");
        p.setProperty("trustCertificateKeyStoreUrl",file.toUri().toString());if(key!=null)p.setProperty(key,value);
        var set=new DefaultPropertySet();set.initializeProperties(p);return set;
    }
    public static void main(String[] args)throws Exception{
        Path file=Files.createTempFile("qa-public-store-test-",".bin");
        try{
            Files.writeString(file,"PUBLIC_SYNTHETIC_STORE");
            System.setProperty("qa.tls.storeUri",file.toUri().toString());
            System.setProperty("qa.tls.storeSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            var good=props(file,null,null);SharedMysqlPinnedSocketFactory.validateProperties("127.0.0.1",3306,good);passed++;
            rejects(()->SharedMysqlPinnedSocketFactory.validateProperties("localhost",3306,good));
            rejects(()->SharedMysqlPinnedSocketFactory.validateProperties("127.0.0.1",13318,good));
            for(var change:new String[][]{{"sslMode","PREFERRED"},{"sslMode","REQUIRED"},{"sslMode","DISABLED"},{"allowPublicKeyRetrieval","true"},{"fallbackToSystemTrustStore","true"},{"tlsVersions","TLSv1.2"},{"trustCertificateKeyStoreType","JKS"}}){
                rejects(()->SharedMysqlPinnedSocketFactory.validateProperties("127.0.0.1",3306,props(file,change[0],change[1])));
            }
            System.setProperty("qa.tls.storeSha256","0".repeat(64));rejects(()->SharedMysqlPinnedSocketFactory.validateProperties("127.0.0.1",3306,good));
            byte[] synthetic="SYNTHETIC_CERTIFICATE_BYTES".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(synthetic));
            if(!SharedMysqlPinnedSocketFactory.matchesPin(synthetic,hash)||SharedMysqlPinnedSocketFactory.matchesPin(synthetic,"0".repeat(64)))throw new AssertionError();passed++;
            System.out.println("QA_PIN_TESTS_PASS="+passed);
        }finally{Files.deleteIfExists(file);}
    }
}
