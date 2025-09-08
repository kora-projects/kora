package ru.tinkoff.kora.openapi.generator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class HttpServerKotlinOpenapiTest extends BaseKotlinOpenapiTest {
    @ParameterizedTest
    @MethodSource("generateParams")
    void test(SwaggerParams params) throws Exception {
        process(
            params.name(),
            "kotlin-server",
            params.spec(),
            params.options()
        );
    }
}
