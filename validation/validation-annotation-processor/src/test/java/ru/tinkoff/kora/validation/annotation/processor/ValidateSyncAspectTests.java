package ru.tinkoff.kora.validation.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidTaz;
import ru.tinkoff.kora.validation.common.ViolationException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidateSyncAspectTests extends ValidateRunner {

    @Test
    void validateInputSyncSuccess() {
        // given
        var service = getValidateSync();

        // then
        assertDoesNotThrow(() -> service.validatedInput(1, "1", new ValidTaz("1")));
        assertDoesNotThrow(() -> service.validatedInput(1, null, new ValidTaz("1")));
        assertDoesNotThrow(() -> service.validatedInputVoid(1, "1", new ValidTaz("1")));
        assertDoesNotThrow(() -> service.validatedInputVoid(1, null, new ValidTaz("1")));
    }

    @Test
    void validateInputSyncFails() {
        // given
        var service = getValidateSync();

        // then
        var e1 = assertThrows(ViolationException.class, () -> service.validatedInput(0, "1", new ValidTaz("1")));
        var e2 = assertThrows(ViolationException.class, () -> service.validatedInput(1, "", new ValidTaz("1")));
        var e3 = assertThrows(ViolationException.class, () -> service.validatedInput(1, "1", new ValidTaz("A")));
        var allViolations = assertThrows(ViolationException.class, () -> service.validatedInput(0, "", new ValidTaz("A")));
        assertEquals(3, allViolations.getViolations().size());
        Assertions.assertThat(e1.getMessage()).contains("Path 'c1' violation");
        Assertions.assertThat(e2.getMessage()).contains("Path 'c2' violation");
        Assertions.assertThat(e3.getMessage()).contains("Path 'c3.number' violation");
        Assertions.assertThat(allViolations.getMessage()).contains("Path 'c1' violation", "Path 'c2' violation", "Path 'c3.number' violation");
    }

    @Test
    void validateOutputSyncSuccess() {
        // given
        var service = getValidateSync();

        // then
        assertDoesNotThrow(() -> service.validatedOutput(new ValidTaz("1"), null));
        assertDoesNotThrow(() -> service.validatedOutputSimple(new ValidTaz("1")));
        assertDoesNotThrow(() -> service.validatedOutputNullable(new ValidTaz("1")));
    }

    @Test
    void validateOutputSyncFails() {
        // given
        var service = getValidateSync();

        // then
        assertThrows(ViolationException.class, () -> service.validatedOutput(new ValidTaz("A"), null));
        assertThrows(ViolationException.class, () -> service.validatedOutput(new ValidTaz("1"), new ValidTaz("1")));
        assertThrows(ViolationException.class, () -> service.validatedOutputSimple(null));
        assertThrows(ViolationException.class, () -> service.validatedOutputNullable(null));
    }

    @Test
    void validateInputOutputSyncSuccess() {
        // given
        var service = getValidateSync();

        // then
        assertDoesNotThrow(() -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), null));
    }

    @Test
    void validateInputOutputSyncFailsForInput() {
        // given
        var service = getValidateSync();

        // then
        assertThrows(ViolationException.class, () -> service.validatedInput(0, "1", new ValidTaz("1")));
        assertThrows(ViolationException.class, () -> service.validatedInput(1, "", new ValidTaz("1")));
        assertThrows(ViolationException.class, () -> service.validatedInput(1, "1", new ValidTaz("A")));
        var inputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(0, "", new ValidTaz("A"), new ValidTaz("1")));
        assertEquals(3, inputViolations.getViolations().size());
    }

    @Test
    void validateInputOutputSyncFailsForOutput() {
        // given
        var service = getValidateSync();

        // then
        var outputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), new ValidTaz("1")));
        assertEquals(1, outputViolations.getViolations().size());
    }

    @Test
    void validateInputOutputSyncFailFastForInput() {
        // given
        var service = getValidateSync();

        // then
        var inputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutputAndFailFast(0, "", new ValidTaz("A"), new ValidTaz("1")));
        assertEquals(1, inputViolations.getViolations().size());
    }

    @Test
    void validateInputOutputSyncFailFastForOutput() {
        // given
        var service = getValidateSync();

        // then
        var outputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutputAndFailFast(1, "1", new ValidTaz("A"), new ValidTaz("1")));
        assertEquals(1, outputViolations.getViolations().size());
    }
}
