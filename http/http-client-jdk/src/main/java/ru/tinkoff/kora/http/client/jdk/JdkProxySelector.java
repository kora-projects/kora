package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.http.client.common.HttpClientConfig;

import java.io.IOException;
import java.net.*;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class JdkProxySelector extends ProxySelector {
    private final HttpClientConfig.HttpClientProxyConfig proxyConfig;
    private final HashSet<String> noProxyHosts;
    private final Proxy proxy;

    public JdkProxySelector(HttpClientConfig.HttpClientProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        this.noProxyHosts = new HashSet<>(Objects.requireNonNullElse(proxyConfig.nonProxyHosts(), List.of()));
        this.proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyConfig.host(), proxyConfig.port()));
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (this.noProxyHosts.contains(uri.getHost())) {
            return List.of(Proxy.NO_PROXY);
        } else {
            return List.of(this.proxy);
        }
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

    }
}
