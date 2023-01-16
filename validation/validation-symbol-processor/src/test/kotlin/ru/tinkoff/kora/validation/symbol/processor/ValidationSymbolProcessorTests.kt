package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.validation.common.ValidationContext
import ru.tinkoff.kora.validation.symbol.processor.testdata.Bar
import ru.tinkoff.kora.validation.symbol.processor.testdata.Foo
import ru.tinkoff.kora.validation.symbol.processor.testdata.Taz
import java.time.OffsetDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class ValidationSymbolProcessorTests : TestAppRunner() {

    @Test
    fun validateSuccess() {
        // give
        val service = getBarValidator()
        val bar = Bar()
        bar.id = "1"
        bar.codes = listOf(1)
        bar.tazs = listOf(Taz("1"))

        // then
        val violations = service.validate(bar);
        assertEquals(0, violations.size)
    }

    @Test
    fun validateRangeFail() {
        // give
        val service = getFooValidator()
        val value = Foo("1", 0, OffsetDateTime.now(), null)

        // then
        val violations = service.validate(value);
        assertEquals(1, violations.size)
    }

    @Test
    fun validateInnerValidatorForListFail() {
        // give
        val service = getBarValidator()
        val value = Bar()
        value.id = "1"
        value.codes = listOf(1)
        value.tazs = listOf(Taz("a"))

        // then
        val violations = service.validate(value);
        assertEquals(1, violations.size)
    }

    @Test
    fun validateInnerValidatorForValueFail() {
        // give
        val service = getFooValidator()
        val bar = Bar().apply {
            id = "1"
            codes = listOf()
        }
        val value = Foo("1", 1, OffsetDateTime.now(), bar)

        // then
        val violations = service.validate(value);
        assertEquals(1, violations.size)
    }

    @Test
    fun validateFailFast() {
        // give
        val service = getFooValidator()
        val bar = Bar().apply {
            id = "1"
            codes = listOf(1, 2, 3, 4, 5, 6)
        }
        val value = Foo("1", 0, OffsetDateTime.now(), bar)

        // then
        val violations = service.validate(value, ValidationContext.builder().failFast(true).build())
        assertEquals(1, violations.size)
    }

    @Test
    fun validateFailSlow() {
        // give
        val service = getFooValidator()
        val bar = Bar().apply {
            id = "1"
            codes = listOf(1, 2, 3, 4, 5, 6)
        }
        val value = Foo("1", 0, OffsetDateTime.now(), bar)

        // then
        val violations = service.validate(value, ValidationContext.builder().failFast(false).build())
        assertEquals(2, violations.size)
    }
}
