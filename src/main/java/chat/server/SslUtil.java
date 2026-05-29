package chat.server;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

public final class SslUtil {
    private SslUtil() {}

    public static SSLServerSocketFactory createServerSocketFactory(Path keystorePath, String keystorePassword) throws Exception {
        if (!Files.exists(keystorePath)) {
            System.err.println("Keystore not found at " + keystorePath.toAbsolutePath());
            System.err.println("To generate a self-signed keystore, run:");
            System.err.println("  keytool -genkey -alias chat-server -keystore " + keystorePath + " -keystore-pass pass:12345678");
            throw new IOException("Keystore not found. Create it using keytool.");
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(keystorePath)) {
            keyStore.load(is, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, keystorePassword.toCharArray());

        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(kmf.getKeyManagers(), null, null);

        return context.getServerSocketFactory();
    }

    public static javax.net.ssl.SSLSocketFactory createClientSocketFactory(Path truststorePath, String truststorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        if (Files.exists(truststorePath)) {
            try (InputStream is = Files.newInputStream(truststorePath)) {
                trustStore.load(is, truststorePassword.toCharArray());
            }
        } else {
            trustStore.load(null, null);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(trustStore);

        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(null, tmf.getTrustManagers(), null);

        return context.getSocketFactory();
    }
}
