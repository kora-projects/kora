package ru.tinkoff.kora.openapi.generator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class HttpClientJavaOpenapiTest extends BaseJavaOpenapiTest {
    @ParameterizedTest
    @MethodSource("generateParams")
    void test(SwaggerParams params) throws Exception {
        process(
            params.name(),
            "java-client",
            params.spec(),
            params.options()
        );
    }
}
