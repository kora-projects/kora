package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.exception.HttpClientConnectionException;
import io.koraframework.http.client.common.exception.HttpClientException;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.exception.HttpClientUnknownException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.EmptyHttpBody;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

public class ApacheHttpClient implements HttpClient {

    private final org.apache.hc.client5.http.classic.HttpClient httpClient;

    public ApacheHttpClient(org.apache.hc.client5.http.classic.HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public HttpClientResponse execute(HttpClientRequest request) {
        try (var _ = request.body()) {
            var apacheRequest = convertToApacheRequest(request);
            var apacheResponse = httpClient.executeOpen(null, apacheRequest, null);
            return new ApacheHttpClientResponse(apacheResponse);
        } catch (HttpClientException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new HttpClientTimeoutException(e);
        } catch (IOException t) {
            throw new HttpClientConnectionException(t);
        } catch (Throwable t) {
            throw new HttpClientUnknownException(t);
        }
    }

    private ClassicHttpRequest convertToApacheRequest(HttpClientRequest request) {
        var apacheRequest = new HttpUriRequestBase(request.method(), request.uri());
        if (request.requestTimeout() != null) {
            apacheRequest.setConfig(RequestConfig.custom()
                .setResponseTimeout(request.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build());
        }

        for (var header : request.headers()) {
            var values = header.getValue();
            for (var value : values) {
                apacheRequest.addHeader(header.getKey(), value);
            }
        }

        if (request.body() != null && !(request.body() instanceof EmptyHttpBody)) {
            apacheRequest.setEntity(new ApacheHttpRequestBody(request));
        }

        return apacheRequest;
    }
}
