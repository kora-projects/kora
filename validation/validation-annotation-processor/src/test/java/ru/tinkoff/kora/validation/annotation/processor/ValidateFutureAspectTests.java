package ru.tinkoff.kora.validation.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidTaz;
import ru.tinkoff.kora.validation.common.ViolationException;

import java.util.concurrent.CompletionException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidateFutureAspectTests extends ValidateRunner {

    @Test
    void validateInputFutureSuccess() {
        // given
        var service = getValidateFuture();

        // then
        assertDoesNotThrow(() -> service.validatedInput(1, "1", new ValidTaz("1")).toCompletableFuture().join());
        assertDoesNotThrow(() -> service.validatedInput(1, null, new ValidTaz("1")).toCompletableFuture().join());
        assertDoesNotThrow(() -> service.validatedInputVoid(1, "1", new ValidTaz("1")).toCompletableFuture().join());
        assertDoesNotThrow(() -> service.validatedInputVoid(1, null, new ValidTaz("1")).toCompletableFuture().join());
    }

    @Test
    void validateInputFutureFails() {
        // given
        var service = getValidateFuture();

        // then
        var e1 = assertThrows(CompletionException.class, () -> service.validatedInput(0, "1", new ValidTaz("1")).toCompletableFuture().join());
        var e2 = assertThrows(CompletionException.class, () -> service.validatedInput(1, "", new ValidTaz("1")).toCompletableFuture().join());
        var e3 = assertThrows(CompletionException.class, () -> service.validatedInput(1, "1", new ValidTaz("A")).toCompletableFuture().join());
        var allViolations = assertThrows(CompletionException.class, () -> service.validatedInput(0, "", new ValidTaz("A")).toCompletableFuture().join());
        assertEquals(3, ((ViolationException) allViolations.getCause()).getViolations().size());
        Assertions.assertThat(e1.getMessage()).contains("Path 'c1' violation");
        Assertions.assertThat(e2.getMessage()).contains("Path 'c2' violation");
        Assertions.assertThat(e3.getMessage()).contains("Path 'c3.number' violation");
        Assertions.assertThat(allViolations.getMessage()).contains("Path 'c1' violation", "Path 'c2' violation", "Path 'c3.number' violation");
    }

    @Test
    void validateOutputFutureSuccess() {
        // given
        var service = getValidateFuture();

        // then
        assertDoesNotThrow(() -> service.validatedOutput(new ValidTaz("1"), null).toCompletableFuture().join());
        assertDoesNotThrow(() -> service.validatedOutputSimple(new ValidTaz("1")).toCompletableFuture().join());
        assertDoesNotThrow(() -> service.validatedOutputNull(new ValidTaz("1")).toCompletableFuture().join());
    }

    @Test
    void validateOutputFutureFails() {
        // given
        var service = getValidateFuture();

        // then
        assertThrows(CompletionException.class, () -> service.validatedOutput(new ValidTaz("A"), null).toCompletableFuture().join());
        assertThrows(CompletionException.class, () -> service.validatedOutput(new ValidTaz("1"), new ValidTaz("1")).toCompletableFuture().join());
        assertThrows(CompletionException.class, () -> service.validatedOutputSimple(null).toCompletableFuture().join());
        assertDoesNotThrow(() -> service.validatedOutputNull(null).toCompletableFuture().join());
    }

    @Test
    void validateInputOutputFutureSuccess() {
        // given
        var service = getValidateFuture();

        // then
        assertDoesNotThrow(() -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), null).toCompletableFuture().join());
    }

    @Test
    void validateInputOutputFutureFailsForInput() {
        // given
        var service = getValidateFuture();

        // then
        assertThrows(CompletionException.class, () -> service.validatedInput(0, "1", new ValidTaz("1")).toCompletableFuture().join());
        assertThrows(CompletionException.class, () -> service.validatedInput(1, "", new ValidTaz("1")).toCompletableFuture().join());
        assertThrows(CompletionException.class, () -> service.validatedInput(1, "1", new ValidTaz("A")).toCompletableFuture().join());
        var inputViolations = assertThrows(CompletionException.class, () -> service.validatedInputAndOutput(0, "", new ValidTaz("A"), new ValidTaz("1")).toCompletableFuture().join());
        assertTrue(inputViolations.getCause() instanceof ViolationException);
        assertEquals(3, ((ViolationException) inputViolations.getCause()).getViolations().size());
    }

    @Test
    void validateInputOutputFutureFailsForOutput() {
        // given
        var service = getValidateFuture();

        // then
        var outputViolations = assertThrows(CompletionException.class, () -> service.validatedInputAndOutput(1, "1", new ValidTaz("1"), new ValidTaz("1")).toCompletableFuture().join());
        assertEquals(1, ((ViolationException) outputViolations.getCause()).getViolations().size());
    }

    @Test
    void validateInputOutputFutureFailFastForInput() {
        // given
        var service = getValidateFuture();

        // then
        var inputViolations = assertThrows(CompletionException.class, () -> service.validatedInputAndOutputAndFailFast(0, "", new ValidTaz("A"), new ValidTaz("1")).toCompletableFuture().join());
        assertEquals(1, ((ViolationException) inputViolations.getCause()).getViolations().size());
    }

    @Test
    void validateInputOutputFutureFailFastForOutput() {
        // given
        var service = getValidateFuture();

        // then
        var outputViolations = assertThrows(CompletionException.class, () -> service.validatedInputAndOutputAndFailFast(1, "1", new ValidTaz("A"), new ValidTaz("1")).toCompletableFuture().join());
        assertEquals(1, ((ViolationException) outputViolations.getCause()).getViolations().size());
    }
}
