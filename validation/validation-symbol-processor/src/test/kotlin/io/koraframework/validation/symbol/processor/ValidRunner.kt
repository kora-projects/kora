package io.koraframework.validation.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import io.koraframework.application.graph.TypeRef
import io.koraframework.ksp.common.symbolProcess
import io.koraframework.validation.common.Validator
import io.koraframework.validation.common.constraint.ValidatorModule
import io.koraframework.validation.symbol.processor.testdata.ValidBar
import io.koraframework.validation.symbol.processor.testdata.ValidFoo
import io.koraframework.validation.symbol.processor.testdata.ValidTaz

@KspExperimental
open class ValidRunner : Assertions(), ValidatorModule {

    companion object {
        private var classLoader: ClassLoader? = null
    }

    protected open fun getFooValidator(): Validator<ValidFoo> {
        val classLoader = getClassLoader()
        val clazz = classLoader.loadClass("io.koraframework.validation.symbol.processor.testdata.\$ValidFoo_Validator")
        return clazz.constructors[0].newInstance(
            notEmptyStringConstraintFactory(),
            patternStringConstraintFactory(),
            rangeLongConstraintFactory(),
            getBarValidator()
        ) as Validator<ValidFoo>
    }

    protected open fun getBarValidator(): Validator<ValidBar> {
        val classLoader = getClassLoader()
        val clazz = classLoader.loadClass("io.koraframework.validation.symbol.processor.testdata.\$ValidBar_Validator")
        return clazz.constructors[0].newInstance(
            notEmptyStringConstraintFactory(),
            sizeListConstraintFactory(TypeRef.of(Int::class.java)),
            listValidator(getTazValidator(), TypeRef.of(ValidTaz::class.java))
        ) as Validator<ValidBar>
    }

    protected open fun getTazValidator(): Validator<ValidTaz> {
        val classLoader = getClassLoader()
        val clazz = classLoader.loadClass("io.koraframework.validation.symbol.processor.testdata.\$ValidTaz_Validator")
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
                classLoader = symbolProcess(listOf(ValidSymbolProcessorProvider()), classes)
            }
            classLoader!!
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }
}
