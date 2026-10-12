package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.request.HttpClientRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour of the client built by {@link ApacheHttpClientWrapper} with its production defaults.
 */
class ApacheHttpClientWrapperTest {

    private static final String PROXY_AUTH = "Basic " + Base64.getEncoder().encodeToString("u:p".getBytes(StandardCharsets.UTF_8));

    private final List<AutoCloseable> closeables = new ArrayList<>();

    @AfterEach
    void closeAll() throws Exception {
        for (var c : closeables) c.close();
    }

    @Test
    @Timeout(20)
    void cookiesAreNotStoredBetweenCalls() throws Exception {
        var srv = server((req, out, s) -> {
            respond(out, 200, "ok", "Set-Cookie: session=user-a; Path=/");
            return true;
        });
        var client = client(null);
        read(client, "http://127.0.0.1:" + srv.port() + "/login");
        read(client, "http://127.0.0.1:" + srv.port() + "/other");

        assertThat(srv.requests).hasSize(2);
        assertThat(srv.requests.get(1).get("cookie")).isNull();
    }

    @Test
    @Timeout(20)
    void proxyBasicAuthHttpTarget() throws Exception {
        var proxy = server((req, out, s) -> {
            if (PROXY_AUTH.equals(req.get("proxy-authorization"))) {
                respond(out, 200, "proxied");
            } else {
                respond(out, 407, "", "Proxy-Authenticate: Basic realm=\"proxy\"");
            }
            return true;
        });
        var client = client(proxy(proxy.port()));

        assertThat(read(client, "http://upstream.example/x")).isEqualTo("200:proxied");
    }

    @Test
    @Timeout(20)
    void proxyBasicAuthHttpsTarget() throws Exception {
        var proxy = server((req, out, s) -> {
            if (PROXY_AUTH.equals(req.get("proxy-authorization"))) {
                return false; // tunnel is not established, the credentials are all we need
            }
            respond(out, 407, "", "Proxy-Authenticate: Basic realm=\"proxy\"");
            return true;
        });
        var client = client(proxy(proxy.port()));
        try {
            read(client, "https://upstream.example/x");
        } catch (Exception ignored) {
        }

        assertThat(proxy.requests).anySatisfy(r -> assertThat(r.get("proxy-authorization")).isEqualTo(PROXY_AUTH));
    }

    @Test
    @Timeout(20)
    void targetUnauthorizedIsReturnedToCaller() throws Exception {
        var srv = server((req, out, s) -> {
            respond(out, 401, "denied", "WWW-Authenticate: Basic realm=\"target\"");
            return true;
        });
        var client = client(null);

        assertThat(read(client, "http://127.0.0.1:" + srv.port() + "/")).isEqualTo("401:denied");
        assertThat(srv.requests).hasSize(1);
    }

    @Test
    @Timeout(20)
    void staleKeepAliveConnectionIsNotAnError() throws Exception {
        var srv = server((req, out, s) -> {
            respond(out, 200, "ok");
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(200);
                    s.close();
                } catch (Exception ignored) {
                }
            });
            return true;
        });
        var client = client(null);

        assertThat(read(client, "http://127.0.0.1:" + srv.port() + "/1")).isEqualTo("200:ok");
        Thread.sleep(1000);
        assertThat(read(client, "http://127.0.0.1:" + srv.port() + "/2")).isEqualTo("200:ok");
    }

    private HttpClient client(HttpClientConfig.@Nullable HttpClientProxyConfig proxy) {
        var wrapper = new ApacheHttpClientWrapper(() -> proxy, new ApacheHttpClientConfig() {}, null, null);
        wrapper.init();
        closeables.add(wrapper::release);
        return new ApacheHttpClient(wrapper.value());
    }

    private static HttpClientConfig.HttpClientProxyConfig proxy(int port) {
        return new HttpClientConfig.HttpClientProxyConfig() {
            @Override
            public String host() {return "127.0.0.1";}

            @Override
            public int port() {return port;}

            @Override
            public @Nullable List<String> nonProxyHosts() {return null;}

            @Override
            public String user() {return "u";}

            @Override
            public String password() {return "p";}
        };
    }

    private static String read(HttpClient client, String uri) throws IOException {
        try (var rs = client.execute(HttpClientRequest.get(uri).build()); var is = rs.body().asInputStream()) {
            return rs.code() + ":" + new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(OutputStream out, int code, String body, String... headers) throws IOException {
        var b = body.getBytes(StandardCharsets.UTF_8);
        var sb = new StringBuilder("HTTP/1.1 ").append(code).append(" X\r\n");
        for (var h : headers) sb.append(h).append("\r\n");
        sb.append("Content-Length: ").append(b.length).append("\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    private interface Handler {
        /** writes a response; false closes the connection */
        boolean handle(Map<String, String> headers, OutputStream out, Socket socket) throws Exception;
    }

    private RawServer server(Handler handler) throws IOException {
        var srv = new RawServer(handler);
        closeables.add(srv);
        return srv;
    }

    /** a bodyless HTTP/1.1 server that records request headers (lower-cased names) */
    private static final class RawServer implements AutoCloseable {
        private final ServerSocket ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();

        RawServer(Handler handler) throws IOException {
            Thread.ofVirtual().start(() -> {
                while (!ss.isClosed()) {
                    try {
                        var s = ss.accept();
                        sockets.add(s);
                        Thread.ofVirtual().start(() -> serve(s, handler));
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        int port() {return ss.getLocalPort();}

        private void serve(Socket s, Handler handler) {
            try (s) {
                var in = new BufferedInputStream(s.getInputStream());
                while (true) {
                    var line = readLine(in);
                    if (line == null) return;
                    if (line.isEmpty()) continue;
                    var headers = new TreeMap<String, String>();
                    String h;
                    while ((h = readLine(in)) != null && !h.isEmpty()) {
                        var i = h.indexOf(':');
                        headers.put(h.substring(0, i).trim().toLowerCase(Locale.ROOT), h.substring(i + 1).trim());
                    }
                    requests.add(headers);
                    if (!handler.handle(headers, s.getOutputStream(), s)) return;
                }
            } catch (Exception ignored) {
            }
        }

        private static @Nullable String readLine(InputStream in) throws IOException {
            var sb = new StringBuilder();
            int c;
            while ((c = in.read()) >= 0) {
                if (c == '\n') {
                    return sb.toString().stripTrailing();
                }
                sb.append((char) c);
            }
            return sb.isEmpty() ? null : sb.toString();
        }

        @Override
        public void close() throws IOException {
            ss.close();
            for (var s : sockets) s.close();
        }
    }
}
