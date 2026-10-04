package house.indie.legacy;

import android.content.Context;
import android.os.Build;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Adds the missing Let's Encrypt root and TLS 1.2 on old Android without bypassing TLS checks. */
final class LegacyTls {
    private static volatile SSLSocketFactory legacyFactory;

    private LegacyTls() { }

    static void configure(HttpsURLConnection connection, Context context) throws Exception {
        if (Build.VERSION.SDK_INT >= 25) return;
        connection.setSSLSocketFactory(factory(context));
        // HttpsURLConnection retains its normal hostname verifier.
    }

    private static SSLSocketFactory factory(Context context) throws Exception {
        if (legacyFactory != null) return legacyFactory;
        synchronized (LegacyTls.class) {
            if (legacyFactory != null) return legacyFactory;

            TrustManagerFactory systemFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            systemFactory.init((KeyStore) null);
            final X509TrustManager system = x509(systemFactory.getTrustManagers());

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            InputStream input = context.getResources().openRawResource(R.raw.isrg_root_x1);
            Certificate root;
            try { root = certificateFactory.generateCertificate(input); }
            finally { input.close(); }
            KeyStore bundled = KeyStore.getInstance(KeyStore.getDefaultType());
            bundled.load(null, null);
            bundled.setCertificateEntry("isrg-root-x1", root);
            TrustManagerFactory bundledFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            bundledFactory.init(bundled);
            final X509TrustManager extra = x509(bundledFactory.getTrustManagers());

            X509TrustManager combined = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    system.checkClientTrusted(chain, authType);
                }

                @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    try { system.checkServerTrusted(chain, authType); }
                    catch (CertificateException missingSystemRoot) {
                        extra.checkServerTrusted(chain, authType);
                    }
                }

                @Override public X509Certificate[] getAcceptedIssuers() {
                    X509Certificate[] original = system.getAcceptedIssuers();
                    X509Certificate[] additional = extra.getAcceptedIssuers();
                    X509Certificate[] result = new X509Certificate[original.length + additional.length];
                    System.arraycopy(original, 0, result, 0, original.length);
                    System.arraycopy(additional, 0, result, original.length, additional.length);
                    return result;
                }
            };
            SSLContext tls = SSLContext.getInstance("TLSv1.2");
            tls.init(null, new TrustManager[] { combined }, null);
            legacyFactory = new ModernProtocols(tls.getSocketFactory());
            return legacyFactory;
        }
    }

    /**
     * Android 4.1-4.4 support TLS 1.1 and 1.2 but leave them off for client
     * sockets, and still offer SSLv3. Servers that require TLS 1.2 (Caddy,
     * stricter Cloudflare settings) would otherwise refuse the handshake.
     */
    private static final class ModernProtocols extends SSLSocketFactory {
        private static final String[] WANTED = {"TLSv1.2", "TLSv1.1", "TLSv1"};
        private final SSLSocketFactory delegate;

        ModernProtocols(SSLSocketFactory delegate) { this.delegate = delegate; }

        private Socket enable(Socket socket) {
            if (!(socket instanceof SSLSocket)) return socket;
            SSLSocket ssl = (SSLSocket) socket;
            List<String> supported = Arrays.asList(ssl.getSupportedProtocols());
            List<String> enabled = new ArrayList<String>();
            for (String protocol : WANTED) if (supported.contains(protocol)) enabled.add(protocol);
            if (!enabled.isEmpty()) ssl.setEnabledProtocols(enabled.toArray(new String[enabled.size()]));
            return ssl;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return enable(delegate.createSocket(socket, host, port, autoClose));
        }
        @Override public Socket createSocket(String host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
            return enable(delegate.createSocket(host, port, local, localPort));
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(InetAddress address, int port, InetAddress local, int localPort) throws IOException {
            return enable(delegate.createSocket(address, port, local, localPort));
        }
    }

    private static X509TrustManager x509(TrustManager[] managers) {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new IllegalStateException("No X.509 trust manager available.");
    }
}
