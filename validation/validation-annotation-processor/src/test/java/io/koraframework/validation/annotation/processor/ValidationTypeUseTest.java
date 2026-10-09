package io.koraframework.validation.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import io.koraframework.validation.common.ViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ValidationTypeUseTest extends AbstractValidationAnnotationProcessorTest {

    private static final String ITEM = """
        @Valid
        public record Item(@Size(min = 1, max = 3) String name) {}
        """;

    @Test
    public void validatorChecksAnnotatedTypeArguments() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            ITEM,
            """
                @Valid
                public record TestRecord(
                    java.util.List<@Size(min = 1, max = 3) String> names,
                    java.util.Map<@NotBlank String, @Valid Item> items,
                    java.util.Map<String, java.util.List<@Valid Item>> nested) {}
                """,
            """
                @KoraApp
                public interface TestApp extends ValidatorModule {
                    @Root
                    default String root(Validator<TestRecord> validator) { return ""; }
                }
                """);
        compileResult.assertSuccess();

        try (var graph = loadGraph("TestApp")) {
            @SuppressWarnings("unchecked")
            var validator = (Validator<Object>) graph.findByType(loadClass("$TestRecord_Validator"));
            var validItem = newObject("Item", "ok");
            var invalidItem = newObject("Item", "too long");

            assertThat(validator.validate(newObject("TestRecord", List.of("ok"), Map.of("key", validItem), Map.of("key", List.of(validItem))))).isEmpty();

            assertThat(paths(validator.validate(newObject("TestRecord", List.of("ok", "too long"), Map.of(), Map.of()))))
                .containsExactly("names.[1]");
            assertThat(paths(validator.validate(newObject("TestRecord", List.of(), Map.of(" ", validItem), Map.of()))))
                .containsExactly("items. ");
            assertThat(paths(validator.validate(newObject("TestRecord", List.of(), Map.of("key", invalidItem), Map.of()))))
                .containsExactly("items.key.name");
            assertThat(paths(validator.validate(newObject("TestRecord", List.of(), Map.of(), Map.of("key", List.of(validItem, invalidItem))))))
                .containsExactly("nested.key.[1].name");
        }
    }

    @Test
    public void validatorChecksValidMapValues() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            ITEM,
            """
                @Valid
                public record TestRecord(@Valid java.util.Map<String, Item> items) {}
                """,
            """
                @KoraApp
                public interface TestApp extends ValidatorModule {
                    @Root
                    default String root(Validator<TestRecord> validator) { return ""; }
                }
                """);
        compileResult.assertSuccess();

        try (var graph = loadGraph("TestApp")) {
            @SuppressWarnings("unchecked")
            var validator = (Validator<Object>) graph.findByType(loadClass("$TestRecord_Validator"));

            assertThat(validator.validate(newObject("TestRecord", Map.of("key", newObject("Item", "ok"))))).isEmpty();
            assertThat(paths(validator.validate(newObject("TestRecord", Map.of("key", newObject("Item", "too long"))))))
                .containsExactly("items.key.name");
        }
    }

    @Test
    public void validateAspectChecksAnnotatedTypeArguments() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            ITEM,
            """
                @Component
                public class TestService {
                    @Validate
                    public java.util.List<@Size(min = 1, max = 3) String> call(java.util.List<@Size(min = 1, max = 3) String> names, java.util.Map<String, @Valid Item> items) {
                        return names;
                    }
                }
                """,
            """
                @KoraApp
                public interface TestApp extends ValidatorModule {
                    @Root
                    default String root(TestService service) { return ""; }
                }
                """);
        compileResult.assertSuccess();

        try (var graph = loadGraph("TestApp")) {
            var service = graph.findByType(loadClass("TestService"));
            var call = service.getClass().getMethod("call", List.class, Map.class);

            assertThat(call.invoke(service, List.of("ok"), Map.of("key", newObject("Item", "ok")))).isEqualTo(List.of("ok"));
            assertThatThrownBy(() -> call.invoke(service, List.of("too long"), Map.of("key", newObject("Item", "too long"))))
                .hasCauseInstanceOf(ViolationException.class)
                .cause()
                .satisfies(e -> assertThat(paths(((ViolationException) e).getViolations())).containsExactly("names.[0]", "items.key.name"));
        }
    }

    private static List<String> paths(List<Violation> violations) {
        return violations.stream().map(v -> v.path().full()).toList();
    }
}
