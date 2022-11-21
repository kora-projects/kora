package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper

interface ReturnType {
    fun responseMapperType(): TypeName {
        val publisherType = publisherType()
        val publisherParameter = publisherParameter().toTypeName().copy(nullable = false)
        val publisherFullType = publisherType.parameterizedBy(publisherParameter)
        return HttpClientResponseMapper::class.asClassName().parameterizedBy(publisherParameter, publisherFullType)
    }

    fun publisherType(): ClassName {
        return Mono::class.asClassName()
    }

    fun publisherParameter(): KSType

    data class MonoReturnType(val simpleReturnType: SimpleReturnType) : ReturnType {
        override fun publisherParameter(): KSType {
            return simpleReturnType.ksType
        }
    }

    data class FluxReturnType(val simpleReturnType: SimpleReturnType) : ReturnType {
        override fun publisherParameter(): KSType {
            return simpleReturnType.ksType
        }

        override fun publisherType(): ClassName {
            return Flux::class.asClassName()
        }

    }

    data class SimpleReturnType(val ksType: KSType) : ReturnType {
        override fun publisherParameter(): KSType {
            return ksType
        }
    }

    data class UnitReturnType(val voidType: KSType) : ReturnType {
        override fun publisherParameter(): KSType {
            return voidType
        }
    }

    data class SuspendReturnType(val returnType: KSType) : ReturnType {
        override fun publisherParameter(): KSType {
            return returnType
        }
    }

    class ReturnTypeParser(
        private val resolver: Resolver
    ) {
        private val unitType = resolver.builtIns.unitType
        private val monoType = resolver.getClassDeclarationByName(Mono::class.qualifiedName!!)!!.asStarProjectedType()
        private val fluxType = resolver.getClassDeclarationByName(Flux::class.qualifiedName!!)!!.asStarProjectedType()
        private val anyType = resolver.builtIns.anyType

        fun parseReturnType(method: KSFunctionDeclaration): ReturnType {
            val methodReturnType = method.returnType!!.resolve()
            if (method.modifiers.contains(Modifier.SUSPEND)) {
                return SuspendReturnType(methodReturnType)
            }
            if (unitType == methodReturnType) {
                return UnitReturnType(unitType)
            }
            if (anyType == methodReturnType) {
                return SimpleReturnType(methodReturnType)
            }
            val methodReturnTypeErasure = methodReturnType.starProjection()
            if (monoType == methodReturnTypeErasure) {
                val simpleType = SimpleReturnType(methodReturnType.arguments[0].type!!.resolve())
                return MonoReturnType(simpleType)
            }
            if (fluxType == methodReturnTypeErasure) {
                val simpleType = SimpleReturnType(methodReturnType.arguments[0].type!!.resolve())
                return FluxReturnType(simpleType)
            }
            return SimpleReturnType(methodReturnType)
        }
    }
}
