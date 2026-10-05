package io.koraframework.validation.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.common.ViolationException;
import io.koraframework.validation.common.constraint.ValidatorModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ValidateSyncAspectTypeTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void resultPrimitiveIntWithRange() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @Range(from = 1, to = 5)
                    public int test(int value) {
                        return value;
                    }
                }
                """);
        compileResult.assertSuccess();

        var component = newObject("$TestComponent__AopProxy", rangeIntegerValidatorFactory());
        assertEquals(3, invoke(component, "test", 3));
        assertThrows(ViolationException.class, () -> invoke(component, "test", 7));
    }

    @Test
    public void resultPrimitiveBooleanWithAssertTrue() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @AssertTrue
                    public boolean test(boolean value) {
                        return value;
                    }
                }
                """);
        compileResult.assertSuccess();

        var component = newObject("$TestComponent__AopProxy", assertTrueValidatorFactory());
        assertEquals(true, invoke(component, "test", true));
        assertThrows(ViolationException.class, () -> invoke(component, "test", false));
    }

    @Test
    public void resultPrimitiveLongWithPositiveFailFast() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    @Positive
                    public long test(long value) {
                        return value;
                    }
                }
                """);
        compileResult.assertSuccess();

        var component = newObject("$TestComponent__AopProxy", positiveValidatorFactory(TypeRef.of(Long.class)));
        assertEquals(1L, invoke(component, "test", 1L));
        assertThrows(ViolationException.class, () -> invoke(component, "test", -1L));
    }

    @Test
    public void genericMethodTypeVariableArgumentAndResult() {
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
}
