package ru.tinkoff.kora.http.common.masking;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MaskUtilsTests {

    @MethodSource("toMaskedStringHeadersTestsInput")
    @ParameterizedTest
    public void toMaskedStringHeadersTests(HttpHeaders headers, Set<String> maskedHeaders, Set<String> expectedResultRows) {
        final String resultString = MaskUtils.toMaskedString(headers, maskedHeaders);
        assertThat(resultString.split("\n")).containsExactlyInAnyOrderElementsOf(expectedResultRows);
    }

    @MethodSource("toMaskedStringQueryParamsStringTestsInput")
    @ParameterizedTest
    public void toMaskedStringQueryParamsStringTests(String rawQuery, Set<String> maskedQueryParams, String expectedResult) {
        final String resultString = MaskUtils.toMaskedString(rawQuery, maskedQueryParams);
        assertThat(resultString).isEqualTo(expectedResult);
    }

    @MethodSource("toMaskedStringQueryParamsMapTestsInput")
    @ParameterizedTest
    public void toMaskedStringQueryParamsMapTests(Map<String, ? extends Collection<String>> queryParams, Set<String> maskedQueryParams, Set<String> expectedResultEntries) {
        final String resultString = MaskUtils.toMaskedString(queryParams, maskedQueryParams);
        assertThat(resultString.split("&")).containsExactlyInAnyOrderElementsOf(expectedResultEntries);
    }

    private static Stream<Arguments> toMaskedStringHeadersTestsInput() {
        return Stream.of(
            Arguments.of(
                HttpHeaders.ofPlain(Map.of("some-header", "val1", "some-header-2", "val2")),
                Set.of("authorization"),
                Set.of("some-header: val1", "some-header-2: val2")
            ),
            Arguments.of(
                HttpHeaders.ofPlain(Map.of("some-header", "val1", "some-header-2", "val2")),
                Set.of("Some-header"),
                Set.of("some-header: ***", "some-header-2: val2")
            ),
            Arguments.of(
                HttpHeaders.ofPlain(Map.of("some-header", "val1", "some-header-2", "val2")),
                Set.of("some-header"),
                Set.of("some-header: ***", "some-header-2: val2")
            ),
            Arguments.of(
                HttpHeaders.ofPlain(Map.of("some-header", "val1", "some-header-2", "val2")),
                Set.of(),
                Set.of("some-header: val1", "some-header-2: val2")
            ),
            Arguments.of(
                HttpHeaders.ofPlain(Map.of("some-header", "val1", "some-header-2", "val2", "Authorization", "val3")),
                Set.of("some-header", "authorization"),
                Set.of("some-header: ***", "some-header-2: val2", "authorization: ***")
            )
        );
    }

    private static Stream<Arguments> toMaskedStringQueryParamsStringTestsInput() {
        return Stream.of(
            Arguments.of("a=1&b=2", Set.of("authorization"), "a=1&b=2"),
            Arguments.of("", Set.of("authorization"), ""),
            Arguments.of("a=1", Set.of(), "a=1"),
            Arguments.of("a=1&authorization=secret&b=2", Set.of("authorization"), "a=1&authorization=***&b=2"),
            Arguments.of("a=1&Authorization=secret&b=2", Set.of("authorization"), "a=1&Authorization=***&b=2"),
            Arguments.of("a=1&authorization=secret&b=2", Set.of("Authorization"), "a=1&authorization=***&b=2"),
            Arguments.of("a=1&authorization=secret&b=2&Authorization", Set.of("Authorization"), "a=1&authorization=***&b=2&Authorization"),
            Arguments.of("a=1&authorization=secret&sessionid=2", Set.of("Authorization", "SessionID"), "a=1&authorization=***&sessionid=***"),
            Arguments.of("url=www%2Eotherexample%2Ecom%3Fb%3D2%26c%3D3&d=4", Set.of("url", "secret-param"), "url=***&d=4")
        );
    }

    private static Stream<Arguments> toMaskedStringQueryParamsMapTestsInput() {
        return Stream.of(
            Arguments.of(
                Map.of("a", List.of("1"), "b", List.of("2")),
                Set.of("authorization"),
                Set.of("a=1", "b=2")
            ),
            Arguments.of(
                Map.of("a", List.of("1"), "b", List.of("2")),
                Set.of(),
                Set.of("a=1", "b=2")
            ),
            Arguments.of(
                Map.of(),
                Set.of("authorization"),
                Set.of("")
            ),
            Arguments.of(
                Map.of("authorization", List.of("secret"), "b", List.of("2")),
                Set.of("authorization"),
                Set.of("authorization=***", "b=2")
            ),
            Arguments.of(
                Map.of("authorization", List.of(), "b", List.of("2")),
                Set.of("authorization"),
                Set.of("authorization=***", "b=2")
            ),
            Arguments.of(
                Map.of("authorization", List.of("secret", "secret"), "b", List.of("2")),
                Set.of("Authorization"),
                Set.of("authorization=***", "b=2")
            ),
            Arguments.of(
                Map.of("authorization", List.of("secret"), "b", List.of("2"), "sessionid", List.of("secret")),
                Set.of("Authorization", "sessionID"),
                Set.of("authorization=***", "b=2", "sessionid=***")
            )
        );
    }
}
