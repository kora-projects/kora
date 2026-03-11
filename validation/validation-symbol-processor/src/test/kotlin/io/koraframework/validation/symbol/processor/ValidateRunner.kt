package io.koraframework.validation.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.application.graph.TypeRef
import io.koraframework.ksp.common.symbolProcess
import io.koraframework.validation.common.Validator
import io.koraframework.validation.common.constraint.ValidatorModule
import io.koraframework.validation.symbol.processor.testdata.ValidTaz
import io.koraframework.validation.symbol.processor.testdata.ValidateSync

@KspExperimental
open class ValidateRunner : Assertions(),
    ValidatorModule {

    companion object {
        private var classLoader: ClassLoader? = null
    }

    protected open fun getValidateSync(): ValidateSync {
        val classLoader = getClassLoader()
        val clazz = classLoader!!.loadClass("io.koraframework.validation.symbol.processor.testdata.\$ValidateSync__AopProxy")
        return clazz.constructors[0].newInstance(
            rangeIntegerConstraintFactory(),
            notEmptyStringConstraintFactory(),
            patternStringConstraintFactory(),
            getTazValidator(),
            sizeListConstraintFactory(TypeRef.of(ValidTaz::class.java)),
            listValidator(getTazValidator(), TypeRef.of(ValidTaz::class.java))
        ) as ValidateSync
    }

    protected open fun getTazValidator(): Validator<ValidTaz> {
        val classLoader = getClassLoader()
        val clazz = classLoader!!.loadClass("io.koraframework.validation.symbol.processor.testdata.\$ValidTaz_Validator")
        return clazz.constructors[0].newInstance(patternStringConstraintFactory()) as Validator<ValidTaz>
    }

    private fun getClassLoader(): ClassLoader {
        return try {
            if (classLoader == null) {
                val classes = listOf(
                    ValidTaz::class,
                    ValidateSync::class,
                )
                classLoader = symbolProcess(listOf(ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()), classes)
            }
            classLoader!!
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }
}
