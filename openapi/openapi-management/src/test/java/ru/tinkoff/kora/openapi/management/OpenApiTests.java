package ru.tinkoff.kora.openapi.management;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class OpenApiTests {

    @Test
    void getAsString() {
        Optional<String> openAPI = ResourceUtils.getFileAsString("openapi1.yaml");
        assertTrue(openAPI.isPresent());

        assertFalse(openAPI.get().isBlank());
    }

    @Test
    void getAsBytes() throws IOException {
        var openAPI = ResourceUtils.getFileAsStream("openapi1.yaml");
        assertNotNull(openAPI);

        assertFalse(new String(openAPI.readAllBytes(), StandardCharsets.UTF_8).isBlank());
    }
}
