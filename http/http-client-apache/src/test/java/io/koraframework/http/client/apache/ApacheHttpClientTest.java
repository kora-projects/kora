package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.common.body.HttpBody;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ApacheHttpClientTest extends HttpClientTest {

    @Test
    void transportFramingHeadersAreOwnedByApacheEntity() {
        var request = HttpClientRequest.post("/")
            .header("Content-Length", "4")
            .header("Transfer-Encoding", "chunked")
            .body(HttpBody.plaintext("test"))
            .build();

        var apacheRequest = ((ApacheHttpClient) createClient(new HttpClientConfig() {
            @Override
            public HttpClientProxyConfig proxy() {
                return null;
            }
        })).convertToApacheRequest(request);

        assertThat(apacheRequest.containsHeader("Content-Length")).isFalse();
        assertThat(apacheRequest.containsHeader("Transfer-Encoding")).isFalse();
        assertThat(apacheRequest.getEntity().getContentLength()).isEqualTo(4);
    }

    @Test
    void charsetIsNotSentAsContentEncoding() {
        server.when(request("/")).respond(response().withBody("ok"));

        call(HttpClientRequest.post("/").body(HttpBody.plaintext("hello")).build())
            .assertCode(200);

        var recorded = server.retrieveRecordedRequests(request("/"))[0];
        assertThat(recorded.getBodyAsString()).isEqualTo("hello");
        assertThat(recorded.containsHeader("Content-Encoding")).isFalse();
    }

    @Test
    void seeOtherAfterPost() {
        server.when(request("/form")).respond(response().withStatusCode(303).withHeader("Location", "/result"));
        server.when(request("/result").withMethod("GET")).respond(response().withBody("result"));

        call(HttpClientRequest.post("/form").body(HttpBody.plaintext("x=1")).build())
            .assertCode(200)
            .assertBody()
            .asString(StandardCharsets.UTF_8)
            .isEqualTo("result");
    }

    @Test
    void temporaryRedirectWithFullBody() {
        server.when(request("/a")).respond(response().withStatusCode(307).withHeader("Location", "/b"));
        server.when(request("/b").withMethod("POST").withBody("data")).respond(response().withBody("resent"));

        call(HttpClientRequest.post("/a").body(HttpBody.plaintext("data")).build())
            .assertCode(200)
            .assertBody()
            .asString(StandardCharsets.UTF_8)
            .isEqualTo("resent");
    }

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        ApacheHttpClient httpClient = new ApacheHttpClient(HttpClientBuilder.create()
            .setDefaultRequestConfig(RequestConfig.custom()
                .setResponseTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build())
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .build())
                .build())
            .build());
        return httpClient;
    }
}
