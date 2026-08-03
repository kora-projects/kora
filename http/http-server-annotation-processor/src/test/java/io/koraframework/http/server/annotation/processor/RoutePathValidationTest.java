package io.koraframework.http.server.annotation.processor;

import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Locale;

class RoutePathValidationTest extends AbstractHttpControllerTest {

    @Test
    void shouldAcceptWildcardInFinalPathSegment() {
        compile("""
            @HttpController("/api")
            public class Controller {
                @HttpRoute(method = "GET", path = "/files/*")
                HttpServerResponse catchAll() {
                    return HttpServerResponse.of(200);
                }

                @HttpRoute(method = "GET", path = "/files/*.js")
                HttpServerResponse extension() {
                    return HttpServerResponse.of(200);
                }

                @HttpRoute(method = "GET", path = "/files/file-*.txt")
                HttpServerResponse infix() {
                    return HttpServerResponse.of(200);
                }

                @HttpRoute(method = "GET", path = "/foo*bar")
                HttpServerResponse embedded() {
                    return HttpServerResponse.of(200);
                }

                @HttpRoute(method = "GET", path = "/tenant/{id}/report-*.json")
                HttpServerResponse parameterized() {
                    return HttpServerResponse.of(200);
                }
            }
            """);
    }

    @Test
    void shouldRejectInvalidWildcardPlacement() {
        var invalidPaths = List.of(
            "/foo/*/bar",
            "/foo/**",
            "/foo/a*b*c",
            "/foo/*/",
            "/foo/{id}/asset-*/detail",
            "/foo/{*}/bar",
            "/foo/{*}",
            "/foo/{id*}"
        );

        for (var path : invalidPaths) {
            var result = compile(List.of(new HttpControllerProcessor()), source("", path));

            Assertions.assertThat(result.isFailed()).as(path).isTrue();
            Assertions.assertThat(result.errors())
                .as(path)
                .extracting(diagnostic -> diagnostic.getMessage(Locale.US))
                .anySatisfy(message -> assertWildcardError(message, path));
        }
    }

    @Test
    void shouldValidateCombinedControllerAndRoutePath() {
        var result = compile(List.of(new HttpControllerProcessor()), source("/api/*", "/details"));

        Assertions.assertThat(result.isFailed()).isTrue();
        Assertions.assertThat(result.errors())
            .extracting(diagnostic -> diagnostic.getMessage(Locale.US))
            .anySatisfy(message -> assertWildcardError(message, "/api/*/details"));
    }

    private static String source(String rootPath, String routePath) {
        return """
            @HttpController("%s")
            public class Controller {
                @HttpRoute(method = "GET", path = "%s")
                HttpServerResponse test() {
                    return HttpServerResponse.of(200);
                }
            }
            """.formatted(rootPath, routePath);
    }

    private static void assertWildcardError(String message, String path) {
        Assertions.assertThat(message)
            .contains("HTTP server route path is invalid")
            .contains(path)
            .contains("Wildcard '*' is only allowed once")
            .contains("Fix:");
    }
}
