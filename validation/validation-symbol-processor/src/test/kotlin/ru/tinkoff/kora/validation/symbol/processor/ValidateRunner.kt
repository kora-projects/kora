package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.validation.common.Validator
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule
import ru.tinkoff.kora.validation.symbol.processor.testdata.ValidTaz
import ru.tinkoff.kora.validation.symbol.processor.testdata.ValidateFlow
import ru.tinkoff.kora.validation.symbol.processor.testdata.ValidateSuspend
import ru.tinkoff.kora.validation.symbol.processor.testdata.ValidateSync

@KspExperimental
open class ValidateRunner : Assertions(),
    ValidatorModule {

    companion object {
        private var classLoader: ClassLoader? = null
    }

    protected open fun getValidateSync(): ValidateSync {
        val classLoader = getClassLoader()
        val clazz = classLoader!!.loadClass("ru.tinkoff.kora.validation.symbol.processor.testdata.\$ValidateSync__AopProxy")
        return clazz.constructors[0].newInstance(
            rangeIntegerConstraintFactory(),
            notEmptyStringConstraintFactory(),
            getTazValidator(),
            sizeListConstraintFactory(TypeRef.of(ValidTaz::class.java)),
            listValidator(getTazValidator(), TypeRef.of(ValidTaz::class.java))
        ) as ValidateSync
    }

    protected open fun getValidateSuspend(): ValidateSuspend{
        val classLoader = getClassLoader()
        val clazz = classLoader!!.loadClass("ru.tinkoff.kora.validation.symbol.processor.testdata.\$ValidateSuspend__AopProxy")
        return clazz.constructors[0].newInstance(
            rangeIntegerConstraintFactory(),
            notEmptyStringConstraintFactory(),
            getTazValidator(),
            sizeListConstraintFactory(TypeRef.of(ValidTaz::class.java)),
            listValidator(getTazValidator(), TypeRef.of(ValidTaz::class.java))
        ) as ValidateSuspend
    }

    protected open fun getValidateFlow(): ValidateFlow{
        val classLoader = getClassLoader()
        val clazz = classLoader!!.loadClass("ru.tinkoff.kora.validation.symbol.processor.testdata.\$ValidateFlow__AopProxy")
        return clazz.constructors[0].newInstance(
            rangeIntegerConstraintFactory(),
            notEmptyStringConstraintFactory(),
            getTazValidator(),
            sizeListConstraintFactory(TypeRef.of(ValidTaz::class.java)),
            listValidator(getTazValidator(), TypeRef.of(ValidTaz::class.java))
        ) as ValidateFlow
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
                    ValidTaz::class,
                    ValidateSync::class,
                    ValidateSuspend::class,
                    ValidateFlow::class,
                )
                classLoader = symbolProcess(classes, ValidSymbolProcessorProvider(), AopSymbolProcessorProvider())
            }
            classLoader!!
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }
}
