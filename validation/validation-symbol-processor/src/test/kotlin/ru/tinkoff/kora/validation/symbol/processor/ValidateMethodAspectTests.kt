package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.function.ThrowingSupplier
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
        assertThrows(ViolationException::class.java) {
            service.validatedInput(
                0, "1", ValidTaz("1")
            )
        }
        assertThrows(ViolationException::class.java) {
            service.validatedInput(
                1, "", ValidTaz("1")
            )
        }
        assertThrows(ViolationException::class.java) {
            service.validatedInput(
                1, "1", ValidTaz("A")
            )
        }
        val allViolations = assertThrows(
            ViolationException::class.java
        ) { service.validatedInput(0, "", ValidTaz("A")) }
        assertEquals(3, allViolations.violations.size)
    }

    @Test
    fun validateOutputSyncSuccess() {
        // given
        val service = getValidateSync()

        // then
        assertDoesNotThrow<List<ValidTaz>> {
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
        assertDoesNotThrow(ThrowingSupplier<List<ValidTaz>> {
            service.validatedInputAndOutput(
                1, "1", ValidTaz("1"), null
            )
        })
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

    @Test
    fun validateInputSuspendSuccess() {
        // give
        val service = getValidateSuspend()

        // then
        assertDoesNotThrow { runBlocking { service.validatedInput(1, "1", ValidTaz("1")) } }
    }

    @Test
    fun validateInputSuspendFails() {
        // given
        val service = getValidateSuspend()

        // then
        assertThrows(ViolationException::class.java) {
            runBlocking {
                service.validatedInput(
                    0, "1", ValidTaz("1")
                )
            }
        }
        assertThrows(ViolationException::class.java) {
            runBlocking {
                service.validatedInput(
                    1, "", ValidTaz("1")
                )
            }
        }
        assertThrows(ViolationException::class.java) {
            runBlocking {
                service.validatedInput(
                    1, "1", ValidTaz("A")
                )
            }
        }
        val allViolations = assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInput(0, "", ValidTaz("A"))
            }
        }
        assertEquals(3, allViolations.violations.size)
    }

    @Test
    fun validateOutputSuspendSuccess() {
        // given
        val service = getValidateSuspend()

        // then
        assertDoesNotThrow<List<ValidTaz>> {
            runBlocking {
                service.validatedOutput(
                    ValidTaz("1"), null
                )
            }
        }
    }

    @Test
    fun validateOutputSuspendFails() {
        // given
        val service = getValidateSuspend()

        // then
        assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedOutput(ValidTaz("A"), null)
            }
        }
        assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedOutput(
                    ValidTaz("1"), ValidTaz("1")
                )
            }
        }
    }

    @Test
    fun validateInputOutputSuspendSuccess() {
        // given
        val service = getValidateSuspend()

        // then
        assertDoesNotThrow(ThrowingSupplier<List<ValidTaz>> {
            runBlocking {
                service.validatedInputAndOutput(
                    1, "1", ValidTaz("1"), null
                )
            }
        })
    }

    @Test
    fun validateInputOutputSuspendFailsForInput() {
        // given
        val service = getValidateSuspend()

        // then
        assertThrows(ViolationException::class.java) {
            runBlocking {
                service.validatedInput(
                    0, "1", ValidTaz("1")
                )
            }
        }
        assertThrows(ViolationException::class.java) {
            runBlocking {
                service.validatedInput(
                    1, "", ValidTaz("1")
                )
            }
        }
        assertThrows(ViolationException::class.java) {
            runBlocking {
                service.validatedInput(
                    1, "1", ValidTaz("A")
                )
            }
        }
        val inputViolations = assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInputAndOutput(
                    0, "", ValidTaz("A"), ValidTaz("1")
                )
            }
        }
        assertEquals(3, inputViolations.violations.size)
    }

    @Test
    fun validateInputOutputSuspendFailsForOutput() {
        // given
        val service = getValidateSuspend()

        // then
        val outputViolations = assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInputAndOutput(
                    1, "1", ValidTaz("1"), ValidTaz("A")
                )
            }
        }
        assertEquals(2, outputViolations.violations.size)
    }

    @Test
    fun validateInputFlowSuccess() {
        // given
        val service = getValidateFlow()

        // then
        assertDoesNotThrow(ThrowingSupplier {
            runBlocking {
                service.validatedInput(
                    1, "1", ValidTaz("1")
                ).first()
            }
        })
    }

    @Test
    fun validateInputFlowFails() {
        // given
        val service = getValidateFlow()

        // then
        assertThrows(
            ViolationException::class.java
        ) { runBlocking { service.validatedInput(0, "1", ValidTaz("1")).first() } }
        assertThrows(
            ViolationException::class.java
        ) { runBlocking { service.validatedInput(1, "", ValidTaz("1")).first() } }
        assertThrows(
            ViolationException::class.java
        ) { runBlocking { service.validatedInput(1, "1", ValidTaz("A")).first() } }
        assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInputAndOutput(
                    1, "1", ValidTaz("1"), ValidTaz("1")
                ).first()
            }
        }
        val allViolations = assertThrows(
            ViolationException::class.java
        ) { runBlocking { service.validatedInput(0, "", ValidTaz("A")).first() } }
        assertEquals(3, allViolations.violations.size)
    }

    @Test
    fun validateOutputFlowSuccess() {
        // given
        val service = getValidateFlow()

        // then
        assertDoesNotThrow(ThrowingSupplier<List<ValidTaz>> {
            runBlocking {
                service.validatedOutput(
                    ValidTaz("1"), null
                ).first()
            }
        })
    }

    @Test
    fun validateOutputFlowFails() {
        // given
        val service = getValidateFlow()

        // then
        assertThrows(
            ViolationException::class.java
        ) { runBlocking { service.validatedOutput(ValidTaz("A"), null).first() } }
        assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedOutput(
                    ValidTaz("1"), ValidTaz("1")
                ).first()
            }
        }
    }

    @Test
    fun validateInputOutputFlowSuccess() {
        // given
        val service = getValidateFlow()

        // then
        assertDoesNotThrow(ThrowingSupplier<List<ValidTaz>> {
            runBlocking {
                service.validatedInputAndOutput(
                    1, "1", ValidTaz("1"), null
                ).first()
            }
        })
    }

    @Test
    fun validateInputOutputFlowFailsForInput() {
        // given
        val service = getValidateFlow()

        // then
        assertThrows(
            ViolationException::class.java
        ) { runBlocking { service.validatedInputAndOutput(0, "1", ValidTaz("1"), null).first() } }
        assertThrows(
            ViolationException::class.java
        ) { runBlocking { service.validatedInputAndOutput(1, "", ValidTaz("1"), null).first() } }
        assertThrows(
            ViolationException::class.java
        ) { runBlocking { service.validatedInputAndOutput(1, "1", ValidTaz("A"), null).first() } }
        assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInputAndOutput(
                    1, "1", ValidTaz("1"), ValidTaz("1")
                ).first()
            }
        }
        val inputViolations = assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInputAndOutput(
                    0, "", ValidTaz("A"), ValidTaz("1")
                ).first()
            }
        }
        assertEquals(3, inputViolations.violations.size)
    }

    @Test
    fun validateInputOutputFlowFailsForOutput() {
        // given
        val service = getValidateFlow()

        // then
        val outputViolations = assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInputAndOutput(
                    1, "1", ValidTaz("1"), ValidTaz("A")
                ).first()
            }
        }
        assertEquals(2, outputViolations.violations.size)
    }

    @Test
    fun validateInputOutputFlowFailFastForInput() {
        // given
        val service = getValidateFlow()

        // then
        val inputViolations = assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInputAndOutputAndFailFast(
                    0, "", ValidTaz("A"), ValidTaz("1")
                ).first()
            }
        }
        assertEquals(1, inputViolations.violations.size)
    }

    @Test
    fun validateInputOutputFlowFailFastForOutput() {
        // given
        val service = getValidateFlow()

        // then
        val outputViolations = assertThrows(
            ViolationException::class.java
        ) {
            runBlocking {
                service.validatedInputAndOutputAndFailFast(
                    1, "1", ValidTaz("A"), ValidTaz("1")
                ).first()
            }
        }
        assertEquals(1, outputViolations.violations.size)
    }
}
