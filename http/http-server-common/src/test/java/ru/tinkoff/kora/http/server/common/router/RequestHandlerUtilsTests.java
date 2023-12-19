package ru.tinkoff.kora.http.server.common.router;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils;

import java.util.List;
import java.util.Map;

class RequestHandlerUtilsTests {

    // encoding %2F - / (slash)
    static List<Arguments> dataWhenDefault() {
        return List.of(
            Arguments.of("bar", "bar"),
            Arguments.of("b%2Far", "b/ar"),
            Arguments.of("b%2F%2F%2Far", "b///ar"),
            Arguments.of("b%2Fa%2Fr%2F", "b/a/r/"),
            Arguments.of("%2Fbar%2F", "/bar/"),
            Arguments.of("%2Fb%%%ar%2F", "/b%%%ar/"),
            Arguments.of("%%2F%bar%2F", "%/%bar/"),
            Arguments.of("%%2Fbar%2F%", "%/bar/%")
        );
    }

    @ParameterizedTest
    @MethodSource("dataWhenDefault")
    void testParseStringPathParameterEncodedSlash(String input, String expected) {
        var request = Mockito.mock(HttpServerRequest.class);
        Mockito.when(request.pathParams()).thenReturn(Map.of("bar", input));

        var value = RequestHandlerUtils.parseStringPathParameter(request, "bar");

        Assertions.assertThat(value).isEqualTo(expected);
        if (expected.equals(input)) {
            Assertions.assertThat(value).isSameAs(input);
        }
    }
}
