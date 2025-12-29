package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.validation.common.ViolationException
import ru.tinkoff.kora.validation.symbol.processor.testdata.ValidTaz

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class ValidateMethodAspectTests : ValidateRunner() {

    @Test
    fun validateInputSyncSuccess() {
        // give
        val service = getValidateSync()

        // then
        assertDoesNotThrow { service.validatedInput(1, "1", ValidTaz("1")) }
    }

    @Test
    fun validateInputSyncFails() {
        // given
        val service = getValidateSync()

        // then
        val e1 = assertThrows(ViolationException::class.java) {
            service.validatedInput(
                0, "1", ValidTaz("1")
            )
        }
        val e2 = assertThrows(ViolationException::class.java) {
            service.validatedInput(
                1, "", ValidTaz("1")
            )
        }
        val e3 = assertThrows(ViolationException::class.java) {
            service.validatedInput(
                1, "1", ValidTaz("A")
            )
        }
        val allViolations = assertThrows(
            ViolationException::class.java
        ) { service.validatedInput(0, "", ValidTaz("A")) }
        assertEquals(3, allViolations.violations.size)
        Assertions.assertThat(e1.message).contains("Path 'c1' violation")
        Assertions.assertThat(e2.message).contains("Path 'c2' violation")
        Assertions.assertThat(e3.message).contains("Path 'c3.number' violation")
        Assertions.assertThat(allViolations.message).contains("Path 'c1' violation", "Path 'c2' violation", "Path 'c3.number' violation")
    }

    @Test
    fun validateOutputSyncSuccess() {
        // given
        val service = getValidateSync()

        // then
        assertDoesNotThrow {
            service.validatedOutput(
                ValidTaz("1"), null
            )
        }
    }

    @Test
    fun validateOutputSyncFails() {
        // given
        val service = getValidateSync()

        // then
        assertThrows(
            ViolationException::class.java
        ) { service.validatedOutput(ValidTaz("A"), null) }
        assertThrows(
            ViolationException::class.java
        ) {
            service.validatedOutput(
                ValidTaz("1"), ValidTaz("1")
            )
        }
    }

    @Test
    fun validateInputOutputSyncSuccess() {
        // given
        val service = getValidateSync()

        // then
        assertDoesNotThrow {
            service.validatedInputAndOutput(
                1, "1", ValidTaz("1"), null
            )
        }
    }

    @Test
    fun validateInputOutputSyncFailsForInput() {
        // given
        val service = getValidateSync()

        // then
        assertThrows(ViolationException::class.java) {
            service.validatedInputAndOutput(
                0, "1", ValidTaz("1"), null
            )
        }
        assertThrows(ViolationException::class.java) {
            service.validatedInputAndOutput(
                1, "", ValidTaz("1"), null
            )
        }
        assertThrows(ViolationException::class.java) {
            service.validatedInputAndOutput(
                1, "1", ValidTaz("A"), null
            )
        }
        val inputViolations = assertThrows(
            ViolationException::class.java
        ) {
            service.validatedInputAndOutput(
                0, "", ValidTaz("A"), ValidTaz("1")
            )
        }
        assertEquals(3, inputViolations.violations.size)
    }

    @Test
    fun validateInputOutputSyncFailFastForInput() {
        // given
        val service = getValidateSync()

        // then
        val inputViolations = assertThrows(
            ViolationException::class.java
        ) {
            service.validatedInputAndOutputAndFailFast(
                0, "", ValidTaz("A"), ValidTaz("1")
            )
        }
        assertEquals(1, inputViolations.violations.size)
    }

    @Test
    fun validateInputOutputSyncFailsForOutput() {
        // given
        val service = getValidateSync()

        // then
        val outputViolations = assertThrows(
            ViolationException::class.java
        ) {
            service.validatedInputAndOutput(
                1, "1", ValidTaz("1"), ValidTaz("A")
            )
        }
        assertEquals(2, outputViolations.violations.size)
    }

    @Test
    fun validateInputOutputSyncFailFastForOutput() {
        // given
        val service = getValidateSync()

        // then
        val outputViolations = assertThrows(
            ViolationException::class.java
        ) {
            service.validatedInputAndOutputAndFailFast(
                1, "1", ValidTaz("A"), ValidTaz("1")
            )
        }
        assertEquals(1, outputViolations.violations.size)
    }
}
