package app.locaport.files.server;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

// Self-signed cert for the local HTTPS listener; no public CA signs a private IP.
public final class Tls {

    private static final String ALIAS = "locaport";
    private static final long TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000;

    private Tls() {
    }

    public static SSLContext createContext(File keyStoreFile, char[] password) throws Exception {
        KeyStore keyStore;
        if (keyStoreFile.isFile()) {
            keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream in = new FileInputStream(keyStoreFile)) {
                keyStore.load(in, password);
            }
            if (!keyStore.containsAlias(ALIAS)) {
                keyStore = generate(keyStoreFile, password);
            }
        } else {
            keyStore = generate(keyStoreFile, password);
        }

        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    private static KeyStore generate(File file, char[] password) throws Exception {
        Provider bc = new BouncyCastleProvider();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", bc);
        generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();

        long now = System.currentTimeMillis();
        X500Name subject = new X500Name("CN=Locaport, O=Locaport, OU=Local file transfer");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                new Date(now - 24 * 60 * 60 * 1000L),
                new Date(now + TEN_YEARS_MS),
                subject,
                keyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider(bc)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(bc)
                .getCertificate(holder);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(ALIAS, keyPair.getPrivate(), password, new Certificate[]{certificate});

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            keyStore.store(out, password);
        }
        if (!file.setReadable(false, false) || !file.setReadable(true, true)
                || !file.setWritable(false, false) || !file.setWritable(true, true)) {
            // Best effort; some filesystems ignore these modes.
        }
        return keyStore;
    }
}
