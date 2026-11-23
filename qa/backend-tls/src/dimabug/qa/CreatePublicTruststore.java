package dimabug.qa;

import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** Imports ONLY a public CA. No private key or application credentials. Never overwrites. */
public class CreatePublicTruststore {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("QA_ARGUMENTS");
        Path ca = Path.of(args[0]), destination = Path.of(args[1]);
        X509Certificate certificate;
        try (var input = Files.newInputStream(ca)) {
            certificate = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        certificate.checkValidity();
        if (certificate.getBasicConstraints() < 0) throw new IllegalArgumentException("QA_CA_REQUIRED");
        KeyStore store = KeyStore.getInstance("JKS");
        char[] integrity = "qa-public-ca-integrity".toCharArray();
        store.load(null, integrity);
        store.setCertificateEntry("dimabug-qa-ca", certificate);
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) { store.store(output, integrity); }
        System.out.println("QA_PUBLIC_TRUSTSTORE_CREATED");
    }
}
