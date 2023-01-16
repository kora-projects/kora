package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Violation;
import ru.tinkoff.kora.validation.annotation.processor.testdata.Bar;
import ru.tinkoff.kora.validation.annotation.processor.testdata.Foo;
import ru.tinkoff.kora.validation.annotation.processor.testdata.Taz;

import java.time.OffsetDateTime;
import java.util.List;

class ValidationAnnotationProcessorTests extends TestAppRunner {

    @Test
    void validateSuccess() {
        // given
        var service = getBarValidator();
        final Bar value = new Bar()
            .setId("1")
            .setCodes(List.of(1))
            .setTazs(List.of(
                new Taz("1")
            ));

        // then
        final List<Violation> violations = service.validate(value);
        assertEquals(0, violations.size());
    }

    @Test
    void validateRangeFail() {
        // given
        var service = getFooValidator();
        var value = new Foo("1", 1111L, OffsetDateTime.now(), null);

        // then
        final List<Violation> violations = service.validate(value);
        assertEquals(1, violations.size());
    }

    @Test
    void validateInnerValidatorForListFail() {
        // given
        var service = getBarValidator();
        var value = new Bar()
            .setId("1")
            .setCodes(List.of(1))
            .setTazs(List.of(
                new Taz("a")
            ));

        // then
        final List<Violation> violations = service.validate(value);
        assertEquals(1, violations.size());
    }

    @Test
    void validateInnerValidatorForValueFail() {
        // given
        var service = getFooValidator();
        var value = new Foo("1", 1L, OffsetDateTime.now(), new Bar()
            .setId("1")
            .setCodes(List.of(1, 2, 3, 4, 5, 6)));

        // then
        final List<Violation> violations = service.validate(value);
        assertEquals(1, violations.size());
    }

    @Test
    void validateFailFast() {
        // given
        var service = getFooValidator();
        var value = new Foo("1", 0L, OffsetDateTime.now(), new Bar()
            .setId("1")
            .setCodes(List.of(1, 2, 3, 4, 5, 6)));

        // then
        final List<Violation> violations = service.validate(value, ValidationContext.builder().failFast(true).build());
        assertEquals(1, violations.size());
    }

    @Test
    void validateFailSlow() {
        // given
        var service = getFooValidator();
        var value = new Foo("1", 0L, OffsetDateTime.now(),
            new Bar()
                .setId("1")
                .setCodes(List.of(1, 2, 3, 4, 5, 6)));

        // then
        final List<Violation> violations = service.validate(value, ValidationContext.builder().failFast(false).build());
        assertEquals(2, violations.size());
    }
}
