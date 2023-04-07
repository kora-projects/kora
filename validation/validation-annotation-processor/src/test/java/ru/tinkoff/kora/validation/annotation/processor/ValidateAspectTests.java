package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidTaz;
import ru.tinkoff.kora.validation.common.ViolationException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidateAspectTests extends ValidateRunner {

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
        assertThrows(ViolationException.class, () -> service.validatedInput(0, "1", new ValidTaz("1")));
        assertThrows(ViolationException.class, () -> service.validatedInput(1, "", new ValidTaz("1")));
        assertThrows(ViolationException.class, () -> service.validatedInput(1, "1", new ValidTaz("A")));
        var allViolations = assertThrows(ViolationException.class, () -> service.validatedInput(0, "", new ValidTaz("A")));
        assertEquals(3, allViolations.getViolations().size());
    }

    @Test
    void validateOutputSyncSuccess() {
        // given
        var service = getValidateSync();

        // then
        assertDoesNotThrow(() -> service.validatedOutput(new ValidTaz("1"), null));
        assertDoesNotThrow(() -> service.validatedOutputSimple(new ValidTaz("1")));
    }

    @Test
    void validateOutputSyncFails() {
        // given
        var service = getValidateSync();

        // then
        assertThrows(ViolationException.class, () -> service.validatedOutput(new ValidTaz("A"), null));
        assertThrows(ViolationException.class, () -> service.validatedOutput(new ValidTaz("1"), new ValidTaz("1")));
        assertThrows(ViolationException.class, () -> service.validatedOutputSimple(null));
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

    @Test
    void validateInputFluxSuccess() {
        // given
        var service = getValidateFlux();

        // then
        assertDoesNotThrow(() -> service.validatedInput(1, "1", new ValidTaz("1")).collectList().block());
        assertDoesNotThrow(() -> service.validatedInput(1, null, new ValidTaz("1")).collectList().block());
    }

    @Test
    void validateInputFluxFails() {
        // given
        var service = getValidateFlux();

        // then
        assertThrows(ViolationException.class, () -> service.validatedInput(0, "1", new ValidTaz("1")).collectList().block());
        assertThrows(ViolationException.class, () -> service.validatedInput(1, "", new ValidTaz("1")).collectList().block());
        assertThrows(ViolationException.class, () -> service.validatedInput(1, "1", new ValidTaz("A")).collectList().block());
        var allViolations = assertThrows(ViolationException.class, () -> service.validatedInput(0, "", new ValidTaz("A")).collectList().block());
        assertEquals(3, allViolations.getViolations().size());
    }


    @Test
    void validateOutputFluxSuccess() {
        // given
        var service = getValidateFlux();

        // then
        assertDoesNotThrow(() -> service.validatedOutput(new ValidTaz("1"), null).collectList().block());
        assertDoesNotThrow(() -> service.validatedOutputSimple(new ValidTaz("1")).collectList().block());
    }

    @Test
    void validateOutputFluxFails() {
        // given
        var service = getValidateFlux();

        // then
        assertThrows(ViolationException.class, () -> service.validatedOutput(new ValidTaz("A"), null).collectList().block());
        assertThrows(ViolationException.class, () -> service.validatedOutput(new ValidTaz("1"), new ValidTaz("1")).collectList().block());
        assertThrows(ViolationException.class, () -> service.validatedOutputSimple(null).collectList().block());
    }

    @Test
    void validateInputOutputFluxSuccess() {
        // given
        var service = getValidateFlux();

        // then
        assertDoesNotThrow(() -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), null).collectList().block());
    }

    @Test
    void validateInputOutputFluxFailsForInput() {
        // given
        var service = getValidateFlux();

        // then
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(0, "1", new ValidTaz("1"), null).collectList().block());
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "", new ValidTaz("1"), null).collectList().block());
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("A"), null).collectList().block());
        assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), new ValidTaz("A")).collectList().block());
        var inputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(0, "", new ValidTaz("A"), new ValidTaz("1")).collectList().block());
        assertEquals(3, inputViolations.getViolations().size());
    }

    @Test
    void validateInputOutputFluxFailsForOutput() {
        // given
        var service = getValidateFlux();

        // then
        var outputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), new ValidTaz("1")).collectList().block());
        assertEquals(1, outputViolations.getViolations().size());
    }

    @Test
    void validateInputOutputFluxFailFastForInput() {
        // given
        var service = getValidateFlux();

        // then
        var inputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutputAndFailFast(0, "", new ValidTaz("A"), new ValidTaz("1")).collectList().block());
        assertEquals(1, inputViolations.getViolations().size());
    }

    @Test
    void validateInputOutputFluxFailFastForOutput() {
        // given
        var service = getValidateFlux();

        // then
        var outputViolations = assertThrows(ViolationException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("A"), new ValidTaz("1")).collectList().block());
        assertEquals(1, outputViolations.getViolations().size());
    }
}
