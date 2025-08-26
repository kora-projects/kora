package ru.tinkoff.kora.openapi.management;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class OpenApiTests {

    @Test
    void getAsString() {
        var openAPI = ResourceUtils.getFileAsString("openapi1.yaml");
        assertNotNull(openAPI);

        assertFalse(openAPI.isBlank());
    }

    @Test
    void getAsBytes() throws IOException {
        try (var openAPI = ResourceUtils.getFileAsStream("openapi1.yaml");) {
            assertNotNull(openAPI);

            assertFalse(new String(openAPI.readAllBytes(), StandardCharsets.UTF_8).isBlank());
        }
    }
}
