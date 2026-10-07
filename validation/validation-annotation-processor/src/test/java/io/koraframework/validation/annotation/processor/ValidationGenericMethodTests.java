package io.koraframework.validation.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.ViolationException;
import io.koraframework.validation.common.constraint.ValidatorModule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationGenericMethodTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void methodTypeVariableArgumentAndResult() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NotBlank
                    public <T extends CharSequence> T test(@NotBlank T value) {
                        return value;
                    }
                }
                """);
        compileResult.assertSuccess();

        var component = newObject("$TestComponent__AopProxy", notBlankCharSequenceValidatorFactory());
        assertEquals("1", invoke(component, "test", "1"));
        assertThrows(ViolationException.class, () -> invoke(component, "test", " "));
    }

    @Test
    public void methodTypeVariableNestedInArgumentAndResult() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @Size(min = 1, max = 2)
                    public <T extends CharSequence> java.util.List<T> test(@Size(min = 1, max = 10) java.util.List<T> value) {
                        return value;
                    }
                }
                """);
        compileResult.assertSuccess();

        var proxy = compileResult.loadClass("$TestComponent__AopProxy");
        assertEquals(
            "io.koraframework.validation.common.constraint.factory.SizeValidatorFactory<java.util.List<java.lang.CharSequence>>",
            proxy.getDeclaredConstructors()[0].getGenericParameterTypes()[0].getTypeName());

        var component = newObject("$TestComponent__AopProxy", sizeListValidatorFactory(TypeRef.of(CharSequence.class)));
        assertEquals(List.of("1"), invoke(component, "test", List.of("1")));
        assertThrows(ViolationException.class, () -> invoke(component, "test", List.of()));
        assertThrows(ViolationException.class, () -> invoke(component, "test", List.of("1", "2", "3")));
    }

    @Test
    public void methodTypeVariableNestedInValidArgument() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Valid
                record Dto(@NotBlank String name) {}
                """,
            """
                @Component
                public class TestComponent {
                    @Validate
                    public <T extends Dto> void test(@Valid java.util.List<T> value) {
                    }
                }
                """);
        compileResult.assertSuccess();

        var proxy = compileResult.loadClass("$TestComponent__AopProxy");
        assertEquals(
            "io.koraframework.validation.common.Validator<java.util.List<" + testPackage() + ".Dto>>",
            proxy.getDeclaredConstructors()[0].getGenericParameterTypes()[0].getTypeName());

        Validator<List<?>> validator = (value, ctx) -> value.isEmpty() ? List.of(ctx.violates("empty")) : List.of();
        var component = newObject("$TestComponent__AopProxy", validator);
        assertDoesNotThrow(() -> invoke(component, "test", List.of(newObject("Dto", "1"))));
        assertThrows(ViolationException.class, () -> invoke(component, "test", List.of()));
    }

    @Test
    public void methodTypeVariableArrayArgument() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public <T extends CharSequence> void test(@Valid T[] value) {
                    }
                }
                """);
        compileResult.assertSuccess();

        var proxy = compileResult.loadClass("$TestComponent__AopProxy");
        assertEquals(
            "io.koraframework.validation.common.Validator<java.lang.CharSequence[]>",
            proxy.getDeclaredConstructors()[0].getGenericParameterTypes()[0].getTypeName());

        Validator<CharSequence[]> validator = (value, ctx) -> Arrays.asList(value).contains("") ? List.of(ctx.violates("blank")) : List.of();
        var component = newObject("$TestComponent__AopProxy", validator);
        assertDoesNotThrow(() -> invoke(component, "test", (Object) new String[]{"1"}));
        assertThrows(ViolationException.class, () -> invoke(component, "test", (Object) new String[]{""}));
    }
}
