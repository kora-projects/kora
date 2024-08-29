package ru.tinkoff.kora.http.common;

/**
 * <b>Русский</b>: Описывает стандартные HTTP методы
 * <hr>
 * <b>English</b>: Describes standard HTTP methods
 */
public final class HttpMethod {

    private HttpMethod() { }

    public static final String GET = "GET";
    public static final String HEAD = "HEAD";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String CONNECT = "CONNECT";
    public static final String OPTIONS = "OPTIONS";
    public static final String TRACE = "TRACE";
    public static final String PATCH = "PATCH";
}
