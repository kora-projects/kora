package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.common.Component;

@Component
public class UndertowCorsFilter implements HttpHandler {

    /**
     * The main CORS header indicating if cross-origin access is allowed.
     *
     * <p>If it's value is equal to the requesting origin, cross-origin access from that origin is allowed.
     * If it differs, cross-origin access is denied. "*" allows all resources, but is only valid for requests that
     * do not include credentials (Authorization header, session cookie).</p>
     *
     * <p>This filter simply echoes the origin of the request if the request was allowed by the
     * selected policy class, because this is valid under all circumstances.</p>
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Allow-Origin"
     * >Access-Control-Allow_Origin (MDN)</a>
     */
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

    /**
     * Indicates whether cross-origin access with credentials (Authorization header, cookies) is allowed.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Allow-Credentials"
     * >Access-Control-Allow-Credentials (MDN)</a>
     */
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

    /**
     * Used in response to a preflight request to indicate which HTTP headers can be used
     * when making the actual request.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Allow-Headers"
     * >Access-Control-Allow-Headers (MDN)</a>
     */
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";

    /**
     * Used in response to a preflight request to indicate which HTTP methods can be used when making the actual request.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Allow-Methods"
     * >Access-Control-Allow-Methods (MDN)</a>
     */
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";

    /**
     * Lets a server whitelist headers that browsers are allowed to access.
     *
     * <p>Default headers allowed without needing to be exposed:
     * Cache-Control, Content-Language, Content-Type, Expires, Last-Modified, Pragma</p>
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Expose-Headers"
     * >Access-Control-Expose-Headers (MDN)</a>
     */
    public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    /**
     * The max age header determines how long browsers are allowed to cache the CORS responses.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Max-Age"
     * >Access-Control-Max-Age (MDN)</a>
     */
    public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";

    private final HttpHandler next;
    private final CamundaRestConfig config;

    private final String allowMethods;
    private final String allowHeaders;
    private final String allowCredentials;
    @Nullable
    private final String exposeHeaders;
    private final String maxAge;

    public UndertowCorsFilter(HttpHandler next, CamundaRestConfig config) {
        this.next = next;
        this.config = config;
        this.allowMethods = String.join(",", config.cors().allowMethods());
        this.allowHeaders = String.join(",", config.cors().allowHeaders());
        this.allowCredentials = String.valueOf(config.cors().allowCredentials());
        this.maxAge = String.valueOf(config.cors().maxAge().getSeconds());
        this.exposeHeaders = config.cors().exposeHeaders().isEmpty()
            ? null
            : String.join(",", config.cors().exposeHeaders());
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String origin = getOrigin(exchange);
        applyCorsPolicy(exchange, origin);
        next.handleRequest(exchange);
    }

    protected void applyCorsPolicy(HttpServerExchange exchange, @Nullable String origin) {
        if (!hasHeader(exchange, ACCESS_CONTROL_ALLOW_ORIGIN)) {
            final String allowOrigin;
            if (config.cors().allowOrigin() != null) {
                allowOrigin = config.cors().allowOrigin();
            } else if (origin != null) {
                allowOrigin = origin;
            } else {
                allowOrigin = "*";
            }

            addHeader(exchange, ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin);
        }
        if (!hasHeader(exchange, ACCESS_CONTROL_ALLOW_HEADERS)) {
            addHeader(exchange, ACCESS_CONTROL_ALLOW_HEADERS, allowHeaders);
        }
        if (!hasHeader(exchange, ACCESS_CONTROL_ALLOW_CREDENTIALS)) {
            addHeader(exchange, ACCESS_CONTROL_ALLOW_CREDENTIALS, allowCredentials);
        }
        if (!hasHeader(exchange, ACCESS_CONTROL_ALLOW_METHODS)) {
            addHeader(exchange, ACCESS_CONTROL_ALLOW_METHODS, allowMethods);
        }
        if (exposeHeaders != null) {
            if (!hasHeader(exchange, ACCESS_CONTROL_EXPOSE_HEADERS)) {
                addHeader(exchange, ACCESS_CONTROL_EXPOSE_HEADERS, exposeHeaders);
            }
        }
        if (!hasHeader(exchange, ACCESS_CONTROL_MAX_AGE)) {
            addHeader(exchange, ACCESS_CONTROL_MAX_AGE, maxAge);
        }
    }

    @Nullable
    protected String getOrigin(HttpServerExchange exchange) {
        HeaderValues headers = exchange.getRequestHeaders().get("Origin");
        return headers == null ? null : headers.peekFirst();
    }

    protected boolean hasHeader(HttpServerExchange exchange, String name) {
        return exchange.getResponseHeaders().get(name) != null;
    }

    protected void addHeader(HttpServerExchange exchange, String name, String value) {
        exchange.getResponseHeaders().add(HttpString.tryFromString(name), value);
    }
}
