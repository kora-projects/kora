package io.koraframework.http.client.common.proxy;

import io.koraframework.http.client.common.HttpClientConfig;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fromEnv() reads process environment, so each case runs in a child JVM with a controlled environment.
 */
class EnvProxyConfigTest {
    public static void main(String[] args) {
        var c = HttpClientConfig.HttpClientProxyConfig.fromEnv();
        System.out.print("host=" + c.host() + " port=" + c.port() + " user=" + c.user() + " password=" + c.password() + " nonProxy=" + c.nonProxyHosts());
    }

    private static String run(Map<String, String> env) throws Exception {
        var pb = new ProcessBuilder(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
            "-cp", System.getProperty("java.class.path"), EnvProxyConfigTest.class.getName());
        pb.environment().keySet().removeIf(k -> k.toLowerCase().endsWith("_proxy"));
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        var p = pb.start();
        var out = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor(30, TimeUnit.SECONDS)).isTrue();
        return out;
    }

    @Test
    void httpProxyWithoutPortDefaultsTo80() throws Exception {
        assertThat(run(Map.of("HTTP_PROXY", "http://proxy.corp"))).startsWith("host=proxy.corp port=80 ");
    }

    @Test
    void httpsProxyWithoutPortDefaultsTo443() throws Exception {
        assertThat(run(Map.of("HTTPS_PROXY", "https://proxy.corp"))).startsWith("host=proxy.corp port=443 ");
    }

    @Test
    void userWithoutPassword() throws Exception {
        assertThat(run(Map.of("HTTP_PROXY", "http://bob@proxy.corp:3128")))
            .isEqualTo("host=proxy.corp port=3128 user=bob password=null nonProxy=null");
    }

    @Test
    void userAndPasswordAreUrlDecoded() throws Exception {
        assertThat(run(Map.of("HTTP_PROXY", "http://bob%40corp:p%3Ass@proxy.corp:3128")))
            .isEqualTo("host=proxy.corp port=3128 user=bob@corp password=p:ss nonProxy=null");
    }

    @Test
    void plusInPasswordIsKeptLiteral() throws Exception {
        assertThat(run(Map.of("HTTP_PROXY", "http://bob:pa+ss@proxy.corp:3128")))
            .isEqualTo("host=proxy.corp port=3128 user=bob password=pa+ss nonProxy=null");
    }

    @Test
    void noProxyEntriesAreTrimmed() throws Exception {
        assertThat(run(Map.of("HTTP_PROXY", "http://proxy.corp:3128", "NO_PROXY", "localhost, internal.corp,,")))
            .endsWith("nonProxy=[localhost, internal.corp]");
    }
}
