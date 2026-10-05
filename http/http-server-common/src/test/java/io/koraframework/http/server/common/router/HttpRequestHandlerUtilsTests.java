package io.koraframework.http.server.common.router;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpRequestHandlerUtils;

import java.util.List;
import java.util.Map;

class HttpRequestHandlerUtilsTests {

    // the server leaves only %2F/%2f (slash) and %25 (percent) encoded, each is decoded exactly once
    static List<Arguments> dataWhenDefault() {
        return List.of(
            Arguments.of("bar", "bar"),
            Arguments.of("b%2Far", "b/ar"),
            Arguments.of("b%2far", "b/ar"),
            Arguments.of("%2fb%2F", "/b/"),
            Arguments.of("b%2F%2F%2Far", "b///ar"),
            Arguments.of("b%2Fa%2Fr%2F", "b/a/r/"),
            Arguments.of("%2Fbar%2F", "/bar/"),
            Arguments.of("%2Fb%%%ar%2F", "/b%%%ar/"),
            Arguments.of("%%2F%bar%2F", "%/%bar/"),
            Arguments.of("%%2Fbar%2F%", "%/bar/%"),
            Arguments.of("b%252far", "b%2far"),
            Arguments.of("b%252Far", "b%2Far"),
            Arguments.of("100%25", "100%"),
            Arguments.of("%2525%2F", "%25/")
        );
    }

    @ParameterizedTest
    @MethodSource("dataWhenDefault")
    void testParseStringPathParameterEncodedSlash(String input, String expected) {
        var request = Mockito.mock(HttpServerRequest.class);
        Mockito.when(request.pathParams()).thenReturn(Map.of("bar", input));

        var value = HttpRequestHandlerUtils.parsePathString(request, "bar");

        Assertions.assertThat(value).isEqualTo(expected);
        if (expected.equals(input)) {
            Assertions.assertThat(value).isSameAs(input);
        }
    }
}
