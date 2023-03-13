package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.validation.common.Validator
import ru.tinkoff.kora.validation.common.constraint.ValidationModule
import ru.tinkoff.kora.validation.symbol.processor.testdata.ValidBar
import ru.tinkoff.kora.validation.symbol.processor.testdata.ValidFoo
import ru.tinkoff.kora.validation.symbol.processor.testdata.ValidTaz

@KspExperimental
open class ValidRunner : Assertions(), ValidationModule {

    companion object {
        private var classLoader: ClassLoader? = null
    }

    protected open fun getFooValidator(): Validator<ValidFoo> {
        val classLoader = getClassLoader()
        val clazz = classLoader!!.loadClass("ru.tinkoff.kora.validation.symbol.processor.testdata.\$ValidFoo_Validator")
        return clazz.constructors[0].newInstance(
            patternStringConstraintFactory(),
            notEmptyStringConstraintFactory(),
            rangeLongConstraintFactory(),
            getBarValidator()
        ) as Validator<ValidFoo>
    }

    protected open fun getBarValidator(): Validator<ValidBar> {
        val classLoader = getClassLoader()
        val clazz = classLoader!!.loadClass("ru.tinkoff.kora.validation.symbol.processor.testdata.\$ValidBar_Validator")
        return clazz.constructors[0].newInstance(
            sizeListConstraintFactory(TypeRef.of(Int::class.java)),
            notEmptyStringConstraintFactory(),
            listValidator(getTazValidator(), TypeRef.of(ValidTaz::class.java))
        ) as Validator<ValidBar>
    }

    protected open fun getTazValidator(): Validator<ValidTaz> {
        val classLoader = getClassLoader()
        val clazz = classLoader!!.loadClass("ru.tinkoff.kora.validation.symbol.processor.testdata.\$ValidTaz_Validator")
        return clazz.constructors[0].newInstance(patternStringConstraintFactory()) as Validator<ValidTaz>
    }

    private fun getClassLoader(): ClassLoader {
        return try {
            if (classLoader == null) {
                val classes = listOf(
                    ValidFoo::class,
                    ValidBar::class,
                    ValidTaz::class
                )
                classLoader = symbolProcess(classes, ValidSymbolProcessorProvider())
            }
            classLoader!!
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }
}
