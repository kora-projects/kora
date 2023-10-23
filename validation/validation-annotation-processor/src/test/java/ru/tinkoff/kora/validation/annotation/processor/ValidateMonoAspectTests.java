package ru.tinkoff.kora.validation.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidTaz;
import ru.tinkoff.kora.validation.common.ViolationException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidateMonoAspectTests extends ValidateRunner {

    @Test
    void validateInputMonoSuccess() {
        // given
        var service = getValidateMono();

        // then
        assertDoesNotThrow(() -> service.validatedInput(1, "1", new ValidTaz("1")).block());
        assertDoesNotThrow(() -> service.validatedInput(1, null, new ValidTaz("1")).block());
    }

    @Test
    void validateInputMonoFails() {
        // given
        var service = getValidateMono();

        // then
        assertThrows(ViolationException.class, () -> service.validatedInput(0, "1", new ValidTaz("1")).block());
        assertThrows(ViolationException.class, () -> service.validatedInput(1, "", new ValidTaz("1")).block());
        assertThrows(ViolationException.class, () -> service.validatedInput(1, "1", new ValidTaz("A")).block());
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), new ValidTaz("A")).block());
        var allViolations = assertThrows(ViolationException.class, () -> service.validatedInput(0, "", new ValidTaz("A")).block());
        assertEquals(3, allViolations.getViolations().size());
    }

    @Test
    void validateOutputMonoSuccess() {
        // given
        var service = getValidateMono();

        // then
        assertDoesNotThrow(() -> service.validatedOutput(new ValidTaz("1"), null).block());
        assertDoesNotThrow(() -> service.validatedOutputSimple(new ValidTaz("1")).block());
    }

    @Test
    void validateOutputMonoFails() {
        // given
        var service = getValidateMono();

        // then
        assertThrows(ViolationException.class, () -> service.validatedOutput(new ValidTaz("A"), null).block());
        assertThrows(ViolationException.class, () -> service.validatedOutput(new ValidTaz("1"), new ValidTaz("1")).block());
        assertThrows(ViolationException.class, () -> service.validatedOutputSimple(null).block());
    }

    @Test
    void validateInputOutputMonoSuccess() {
        // given
        var service = getValidateMono();

        // then
        assertDoesNotThrow(() -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), null).block());
    }

    @Test
    void validateInputOutputMonoFailsForInput() {
        // given
        var service = getValidateMono();

        // then
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(0, "1", new ValidTaz("1"), null).block());
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "", new ValidTaz("1"), null).block());
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("A"), null).block());
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), new ValidTaz("A")).block());
        var inputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(0, "", new ValidTaz("A"), new ValidTaz("1")).block());
        assertEquals(3, inputViolations.getViolations().size());
    }

    @Test
    void validateInputOutputMonoFailsForOutput() {
        // given
        var service = getValidateMono();

        // then
        var outputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), new ValidTaz("1")).block());
        assertEquals(1, outputViolations.getViolations().size());
    }
}
