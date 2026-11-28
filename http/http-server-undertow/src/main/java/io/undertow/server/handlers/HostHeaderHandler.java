package io.undertow.server.handlers;

import io.undertow.server.*;
import io.undertow.util.*;

public final class HostHeaderHandler implements HttpHandler {

    public static final HandlerWrapper WRAPPER = new HostHeaderHandlerWrapper();

    public static final String STATUS_NO_HOST_HEADER = "No Host Header";
    public static final String STATUS_TOO_MANY_HOST_HEADERS = "Only One Host Header Allowed";
    public static final String STATUS_MALFORMED_PORT = "Host Header Malformed Port";
    public static final String STATUS_MALFORMED_IP_LITERAL = "Host Header Malformed IP-Literal";
    public static final String STATUS_MALFORMED_IP_LITERAL_BAD_CHARS = "Host Header Bad Characters";
    public static final String STATUS_HOST_NO_MATCH = "URI Host Header NO MATCH";

    private static final boolean[] ALLOWED_REG_NAME_CHARACTERS = new boolean[256];
    private static final boolean[] HEX_CHARACTERS = new boolean[256];
    private static final boolean[] ALLOWED_IPv_FUTURE_CHARACTERS = new boolean[256];

    static {
        for (int i = 0; i < ALLOWED_REG_NAME_CHARACTERS.length; ++i) {
            if ((i >= '0' && i <= '9') || (i >= 'a' && i <= 'z') || (i >= 'A' && i <= 'Z')) {
                ALLOWED_REG_NAME_CHARACTERS[i] = true;
                ALLOWED_IPv_FUTURE_CHARACTERS[i] = true;
            } else {
                switch (i) {
                    case '-': case '.': case '_': case '~': case '!': case '$':
                    case '&': case '\'': case '(': case ')': case '*': case '+':
                    case ',': case ';': case '=': {
                        ALLOWED_REG_NAME_CHARACTERS[i] = true;
                        ALLOWED_IPv_FUTURE_CHARACTERS[i] = true;
                        break;
                    }
                    default:
                        ALLOWED_REG_NAME_CHARACTERS[i] = false;
                        ALLOWED_IPv_FUTURE_CHARACTERS[i] = false;
                }
            }
        }
        ALLOWED_IPv_FUTURE_CHARACTERS[':'] = true;

        for (int i = 0; i < HEX_CHARACTERS.length; ++i) {
            HEX_CHARACTERS[i] = (i >= '0' && i <= '9') || (i >= 'a' && i <= 'f') || (i >= 'A' && i <= 'F');
        }
    }

    private final HttpHandler next;

    public HostHeaderHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final HeaderMap headerMap = exchange.getRequestHeaders();
        final HeaderValues headerValues = headerMap.get(Headers.HOST);
        final HttpString protocol = exchange.getProtocol();

        if (protocol.equals(Protocols.HTTP_0_9) || protocol.equals(Protocols.HTTP_1_0)) {
            if (headerValues == null) {
                next.handleRequest(exchange);
                return;
            }
        }

        if (headerValues == null || headerValues.size() == 0) {
            terminate(exchange, STATUS_NO_HOST_HEADER);
            return;
        } else if (headerValues.size() > 1) {
            terminate(exchange, STATUS_TOO_MANY_HOST_HEADERS);
            return;
        }

        final String headerValue = headerValues.element();
        final int rightBracketIndex = headerValue.lastIndexOf(']');
        final int lastColonIndex = headerValue.lastIndexOf(':');

        final String hostHeaderURI;

        if (rightBracketIndex < lastColonIndex) {
            if (rightBracketIndex == -1 && headerValue.startsWith("[")) {
                terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                return;
            }
            final String portString = headerValue.substring(lastColonIndex + 1);
            try {
                int port = Integer.parseInt(portString);
                if (port <= 0 || port > 65535) {
                    terminate(exchange, STATUS_MALFORMED_PORT);
                    return;
                }
            } catch (NumberFormatException nfe) {
                terminate(exchange, STATUS_MALFORMED_PORT);
                return;
            }
            hostHeaderURI = headerValue.substring(0, lastColonIndex);
        } else {
            hostHeaderURI = headerValue;
        }

        if (rightBracketIndex > 0 && hostHeaderURI.indexOf('[') != 0) {
            terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
            return;
        } else if (rightBracketIndex > 0 && hostHeaderURI.indexOf('[') == 0) {
            final String debracked = hostHeaderURI.substring(1, hostHeaderURI.length() - 1);
            if (debracked.startsWith("v")) {
                final int dotIndex = debracked.indexOf(".");
                if (dotIndex < 2) {
                    terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                    return;
                }
                final String hex = debracked.substring(1, dotIndex);
                for (int i = 0; i < hex.length(); i++) {
                    final char c1 = hex.charAt(i);
                    if (c1 >= 256 || !HEX_CHARACTERS[c1]) {
                        terminate(exchange, STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
                        return;
                    }
                }
                if (dotIndex + 1 >= debracked.length()) {
                    terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                    return;
                }
                for (int i = dotIndex + 1; i < debracked.length(); i++) {
                    final char c = debracked.charAt(i);
                    if (c >= 256 || !ALLOWED_IPv_FUTURE_CHARACTERS[c]) {
                        terminate(exchange, STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
                        return;
                    }
                }
            } else {
                // Быстрый ручной парсинг IPv6 без регулярных выражений
                if (!fastCheckIPv6(debracked)) {
                    terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                    return;
                }
            }
        } else if (fastCheckIPv4(hostHeaderURI)) {
            // Быстрый ручной парсинг IPv4 (если это 4 октета цифр через точку),
            // если проверки пройдены — адрес валиден.
            // Если формат не совпал с классическим IP, проваливаемся в reg-name (доменное имя).
        } else {
            for (int index = 0; index < hostHeaderURI.length(); index++) {
                final char c = hostHeaderURI.charAt(index);
                if (c == '%') {
                    if (index + 2 < hostHeaderURI.length()) {
                        char c1 = hostHeaderURI.charAt(++index);
                        char c2 = hostHeaderURI.charAt(++index);
                        if (c1 >= 256 || c2 >= 256 || !(HEX_CHARACTERS[c1] && HEX_CHARACTERS[c2])) {
                            terminate(exchange, STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
                            return;
                        }
                    } else {
                        terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                        return;
                    }
                } else if (c >= 256 || !ALLOWED_REG_NAME_CHARACTERS[c]) {
                    terminate(exchange, STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
                    return;
                }
            }
        }

        if (exchange.isHostIncludedInRequestURI()) {
            if (!exchange.getRequestURI().contains(hostHeaderURI) || hostHeaderURI.isEmpty()) {
                terminate(exchange, STATUS_HOST_NO_MATCH);
                return;
            }
        }

        next.handleRequest(exchange);
    }

    private void terminate(final HttpServerExchange exchange, final String message) {
        exchange.setStatusCode(StatusCodes.BAD_REQUEST);
        exchange.setResponseContentLength(0);
        exchange.getResponseHeaders().add(Headers.CONNECTION, Headers.CLOSE.toString());
        exchange.setReasonPhrase(message);
        exchange.endExchange();
    }

    private static boolean fastCheckIPv4(String host) {
        int dotCount = 0;
        int currentOctet = 0;
        int octetLength = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') {
                if (octetLength == 0 || currentOctet > 255) return false;
                dotCount++;
                currentOctet = 0;
                octetLength = 0;
            } else if (c >= '0' && c <= '9') {
                if (octetLength >= 3) return false;
                currentOctet = currentOctet * 10 + (c - '0');
                octetLength++;
            } else {
                return false;
            }
        }
        return dotCount == 3 && octetLength > 0 && currentOctet <= 255;
    }

    private static boolean fastCheckIPv6(String host) {
        int len = host.length();
        if (len < 2) return false;
        int colonCount = 0;
        int currentGroupLength = 0;
        boolean hasDoubleColon = false;

        for (int i = 0; i < len; i++) {
            char c = host.charAt(i);
            if (c == ':') {
                colonCount++;
                if (i > 0 && host.charAt(i - 1) == ':') {
                    if (hasDoubleColon) return false;
                    hasDoubleColon = true;
                }
                if (currentGroupLength > 4) return false;
                currentGroupLength = 0;
            } else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                currentGroupLength++;
            } else if (c == '.' && colonCount >= 2) {
                int lastColon = host.lastIndexOf(':');
                return fastCheckIPv4(host.substring(lastColon + 1));
            } else {
                return false;
            }
        }
        if (currentGroupLength > 4) return false;
        return hasDoubleColon ? colonCount <= 7 : colonCount == 7;
    }

    private static final class HostHeaderHandlerWrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new HostHeaderHandler(handler);
        }
    }
}
