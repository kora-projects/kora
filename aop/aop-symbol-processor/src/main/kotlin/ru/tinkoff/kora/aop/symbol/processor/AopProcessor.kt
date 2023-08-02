package ru.tinkoff.kora.aop.symbol.processor

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import ru.tinkoff.kora.ksp.common.*
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import kotlin.reflect.KClass

class AopProcessor(private val aspects: List<KoraAspect>, private val resolver: Resolver) {

    private class TypeFieldFactory(private val resolver: Resolver) : KoraAspect.FieldFactory {
        private val fieldNames: MutableSet<String> = HashSet()
        private val constructorParams: MutableMap<ConstructorParamKey, String> = linkedMapOf()
        private val constructorInitializedParams: MutableMap<ConstructorInitializedParamKey, String> = linkedMapOf()

        private data class ConstructorParamKey(val type: KSType, val annotations: List<AnnotationSpec>, val resolver: Resolver)
        private data class ConstructorInitializedParamKey(val type: KSType, val initBlock: CodeBlock, val resolver: Resolver)

        override fun constructorParam(type: KSType, annotations: List<AnnotationSpec>): String {
            return constructorParams.computeIfAbsent(ConstructorParamKey(type, annotations, resolver)) { key ->
                this.computeFieldName(key.type)!!
            }
        }

        override fun constructorInitialized(type: KSType, initializer: CodeBlock): String {
            return constructorInitializedParams.computeIfAbsent(ConstructorInitializedParamKey(type, initializer, resolver)) { key ->
                this.computeFieldName(key.type)!!
            }
        }

        fun addFields(typeBuilder: TypeSpec.Builder) {
            constructorParams.forEach { (fd, name) ->
                typeBuilder.addProperty(
                    PropertySpec.builder(name, fd.type.toTypeName(), KModifier.PRIVATE, KModifier.FINAL)
                        .initializer(name)
                        .build()
                )
            }
            constructorInitializedParams.forEach { (fd, name) ->
                typeBuilder.addProperty(
                    PropertySpec.builder(name, fd.type.toTypeName(), KModifier.PRIVATE, KModifier.FINAL).build()
                )
            }
        }

        fun enrichConstructor(constructorBuilder: FunSpec.Builder) {
            constructorParams.forEach { (fd, name) ->
                constructorBuilder.addParameter(
                    ParameterSpec.builder(name, fd.type.toTypeName())
                        .addAnnotations(fd.annotations)
                        .build()
                )
            }
            constructorInitializedParams.forEach { (fd, name) ->
                constructorBuilder.addCode("this.%L = %L\n", name, fd.initBlock)
            }
        }

        private fun computeFieldName(type: KSType): String? {
            var qualifiedType = type.makeNotNullable().toClassName().simpleName
            val dotIndex = qualifiedType.lastIndexOf('.')
            val shortName = if (dotIndex < 0) qualifiedType.replaceFirstChar { it.lowercaseChar() }
            else qualifiedType.substring(dotIndex + 1).replaceFirstChar { it.lowercaseChar() }
            for (i in 1 until Int.MAX_VALUE) {
                val name = shortName + i
                if (fieldNames.add(name)) {
                    return name
                }
            }
            // never gonna happen
            return null
        }
    }

    fun applyAspects(classDeclaration: KSClassDeclaration): TypeSpec {
        val constructor = findAopConstructor(classDeclaration) ?: throw ProcessingErrorException("Class has no aop suitable constructor", classDeclaration)
        val typeLevelAspects: ArrayList<KoraAspect> = ArrayList()
        for (am in classDeclaration.annotations) {
            for (aspect in aspects) {
                val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
                val annotationType = am.annotationType.resolve().toClassName().canonicalName
                if (supportedAnnotationTypes.contains(annotationType)) {
                    if (!typeLevelAspects.contains(aspect)) {
                        typeLevelAspects.add(aspect)
                    }
                }
            }
        }
        KoraSymbolProcessingEnv.logger.logging("Type level aspects for ${classDeclaration.qualifiedName!!.asString()}}: {$typeLevelAspects}", classDeclaration)
        val typeFieldFactory = TypeFieldFactory(resolver)
        val aopContext: KoraAspect.AspectContext = KoraAspect.AspectContext(typeFieldFactory)
        val typeBuilder: TypeSpec.Builder = TypeSpec.classBuilder(aopProxyName(classDeclaration))
            .superclass(classDeclaration.toClassName())
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)

        classDeclaration.parseTags().let { tags ->
            if (tags.isNotEmpty()) {
                typeBuilder.addAnnotation(tags.makeTagAnnotationSpec())
            }
        }
        if (classDeclaration.isAnnotationPresent(CommonClassNames.root)) {
            typeBuilder.addAnnotation(CommonClassNames.root)
        }

        val classFunctions = findMethods(classDeclaration) { f ->
            !f.isConstructor() && (f.isPublic() || f.isProtected())
        }

        val methodAspectsApplied = linkedSetOf<KoraAspect>()
        classFunctions.forEach { function ->
            val methodLevelTypeAspects = typeLevelAspects.toMutableList()
            val methodLevelAspects = mutableListOf<KoraAspect>()
            val methodParameterLevelAspects = mutableListOf<KoraAspect>()
            val functionAnnotations = function.annotations.toList()
            functionAnnotations.forEach { am ->
                aspects.forEach { aspect ->
                    val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
                    val annotationType = am.annotationType.resolve().toClassName().canonicalName
                    if (supportedAnnotationTypes.contains(annotationType)) {
                        if (!methodLevelAspects.contains(aspect)) {
                            methodLevelAspects.add(aspect)
                        }
                        methodLevelTypeAspects.remove(aspect)
                    }
                }
            }
            function.parameters.forEach { parameter ->
                parameter.annotations.forEach { am ->
                    aspects.forEach { aspect ->
                        val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
                        val annotationType = am.annotationType.resolve().toClassName().canonicalName
                        if (supportedAnnotationTypes.contains(annotationType)) {
                            if (!methodParameterLevelAspects.contains(aspect) && !methodLevelAspects.contains(aspect)) {
                                methodParameterLevelAspects.add(aspect)
                            }
                            methodLevelTypeAspects.remove(aspect)
                        }
                    }
                }
            }
            if (methodLevelTypeAspects.isEmpty() && methodLevelAspects.isEmpty() && methodParameterLevelAspects.isEmpty()) {
                return@forEach
            }
            KoraSymbolProcessingEnv.logger.logging(
                "Method level aspects for ${classDeclaration.qualifiedName!!.asString()}}#${function.simpleName.asString()}: {$methodLevelAspects}",
                classDeclaration
            )
            val aspectsToApply = methodLevelTypeAspects.toMutableList()
            aspectsToApply.addAll(methodLevelAspects)
            aspectsToApply.addAll(methodParameterLevelAspects)

            var superCall = "super." + function.simpleName.asString()
            val overridenMethod = FunSpec.builder(function.simpleName.asString()).addModifiers(KModifier.OVERRIDE)

            if (function.modifiers.contains(Modifier.SUSPEND)) {
                overridenMethod.addModifiers(KModifier.SUSPEND)
            }

            aspectsToApply.reverse()
            for (aspect in aspectsToApply) {
                val result: KoraAspect.ApplyResult = aspect.apply(function, superCall, aopContext)
                if (result is KoraAspect.ApplyResult.Noop) {
                    continue
                }

                val methodBody: KoraAspect.ApplyResult.MethodBody = result as KoraAspect.ApplyResult.MethodBody
                val methodName = "_" + function.simpleName.asString() + "_AopProxy_" + aspect::class.simpleName
                superCall = methodName
                val f = FunSpec.builder(methodName)
                    .addModifiers(KModifier.PRIVATE)
                    .addCode(methodBody.codeBlock)

                if (function.modifiers.contains(Modifier.SUSPEND)) {
                    f.addModifiers(KModifier.SUSPEND)
                }

                function.parameters.forEach { parameter ->
                    val paramSpec = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.resolve().toTypeName()).build()
                    if (!overridenMethod.parameters.contains(paramSpec)) {
                        overridenMethod.addParameter(paramSpec)
                    }
                    f.addParameter(paramSpec)
                }
                function.typeParameters.forEach { typeParameter ->
                    overridenMethod.addTypeVariable(typeParameter.toTypeVariableName())
                    f.addTypeVariable(typeParameter.toTypeVariableName())
                }
                val returnType = function.returnType!!.resolve()
                f.returns(returnType.toTypeName())
                typeBuilder.addFunction(f.build())
                methodAspectsApplied.add(aspect)
            }

            if (methodAspectsApplied.isNotEmpty()) {
                val b = CodeBlock.builder()
                if (function.returnType!!.resolve() != resolver.builtIns.unitType) {
                    b.add("return ")
                }
                b.add("%L(", superCall)
                for (i in function.parameters.indices) {
                    if (i > 0) {
                        b.add(", ")
                    }
                    val parameter = function.parameters[i]
                    b.add("%L", parameter)
                }
                b.add(")\n")
                overridenMethod.addCode(b.build())
                typeBuilder.addFunction(overridenMethod.build())
            }
        }

        val generatedClasses = mutableListOf<KClass<*>>()
        generatedClasses.add(AopSymbolProcessor::class)
        methodAspectsApplied.forEach { generatedClasses.add(it::class) }

        typeBuilder.generated(generatedClasses)

        if (classDeclaration.isAnnotationPresent(CommonClassNames.component)) {
            typeBuilder.addAnnotation(CommonClassNames.component)
        }

        val constructorBuilder = FunSpec.constructorBuilder()
        for (i in constructor.parameters.indices) {
            val parameter = constructor.parameters[i]
            typeBuilder.addSuperclassConstructorParameter("%L", parameter.name!!.asString())
            val parameterSpec = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.resolve().toTypeName())

            parameter.parseTags().let { tags ->
                if (tags.isNotEmpty()) {
                    parameterSpec.addAnnotation(tags.makeTagAnnotationSpec())
                }
            }

            constructorBuilder.addParameter(parameterSpec.build())
        }
        typeFieldFactory.addFields(typeBuilder)
        typeFieldFactory.enrichConstructor(constructorBuilder)
        typeBuilder.primaryConstructor(constructorBuilder.build())
        return typeBuilder.build()
    }
}
