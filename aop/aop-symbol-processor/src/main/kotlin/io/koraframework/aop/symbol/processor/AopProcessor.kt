package io.koraframework.aop.symbol.processor

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KoraSymbolProcessingEnv
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.resolveToUnderlying
import io.koraframework.ksp.common.TagUtils.parseTag
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.findMethods
import kotlin.reflect.KClass

class AopProcessor(private val aspects: List<KoraAspect>, private val resolver: Resolver) {

    private class TypeFieldFactory(private val resolver: Resolver, reservedNames: Collection<String>) : KoraAspect.FieldFactory {
        private val fieldNames: MutableSet<String> = HashSet(reservedNames)
        private val constructorParams: MutableMap<ConstructorParamKey, String> = linkedMapOf()
        private val constructorInitializedParams: MutableMap<ConstructorInitializedParamKey, String> = linkedMapOf()

        private data class ConstructorParamKey(val type: TypeName, val annotations: List<AnnotationSpec>, val resolver: Resolver)
        private data class ConstructorInitializedParamKey(val type: TypeName, val initBlock: CodeBlock, val resolver: Resolver)

        override fun constructorParam(type: TypeName, annotations: List<AnnotationSpec>): String {
            return constructorParams.computeIfAbsent(ConstructorParamKey(type, annotations, resolver)) { key ->
                this.computeFieldName(key.type)
            }
        }

        override fun constructorInitialized(type: TypeName, initializer: CodeBlock): String {
            return constructorInitializedParams.computeIfAbsent(ConstructorInitializedParamKey(type, initializer, resolver)) { key ->
                this.computeFieldName(key.type)
            }
        }

        fun addFields(typeBuilder: TypeSpec.Builder) {
            constructorParams.forEach { (fd, name) ->
                typeBuilder.addProperty(
                    PropertySpec.builder(name, fd.type, KModifier.PRIVATE, KModifier.FINAL)
                        .initializer(name)
                        .build()
                )
            }
            constructorInitializedParams.forEach { (fd, name) ->
                typeBuilder.addProperty(
                    PropertySpec.builder(name, fd.type, KModifier.PRIVATE, KModifier.FINAL).build()
                )
            }
        }

        fun enrichConstructor(constructorBuilder: FunSpec.Builder) {
            constructorParams.forEach { (fd, name) ->
                constructorBuilder.addParameter(
                    ParameterSpec.builder(name, fd.type)
                        .addAnnotations(fd.annotations)
                        .build()
                )
            }
            constructorInitializedParams.forEach { (fd, name) ->
                constructorBuilder.addCode("this.%L = %L\n", name, fd.initBlock)
            }
        }

        private fun computeFieldName(type: TypeName): String {
            val qualifiedType = if (type is ParameterizedTypeName) type.rawType else type as ClassName
            val shortName = qualifiedType.simpleName.replaceFirstChar { it.lowercaseChar() }
            for (i in 1 until Int.MAX_VALUE) {
                val name = shortName + i
                if (fieldNames.add(name)) {
                    return name
                }
            }
            // never gonna happen
            throw IllegalStateException(
                """
                Kora internal error: failed to allocate a unique AOP proxy field name for type '$qualifiedType'.

                This should be unreachable because field names are generated with an increasing numeric suffix.
                """.trimIndent()
            )
        }
    }

    fun applyAspects(classDeclaration: KSClassDeclaration): TypeSpec {
        val constructor = classDeclaration.findAopConstructor()
            ?: throw ProcessingErrorException(aopConstructorError(classDeclaration), classDeclaration)

        val typeLevelAspects: ArrayList<KoraAspect> = ArrayList()

        for (am in classDeclaration.annotations) {
            val annotationType = am.annotationType.resolveToUnderlying().declaration.qualifiedName?.asString()
                ?: continue

            for (aspect in aspects) {
                val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
                if (supportedAnnotationTypes.contains(annotationType)) {
                    if (!typeLevelAspects.contains(aspect)) {
                        typeLevelAspects.add(aspect)
                    }
                }
            }
        }
        KoraSymbolProcessingEnv.logger.logging("Type level aspects for ${classDeclaration.qualifiedName!!.asString()}}: {$typeLevelAspects}", classDeclaration)
        val typeVariables = classDeclaration.typeParameters.map { it.toTypeVariableName() }
        val typeBuilder: TypeSpec.Builder = TypeSpec.classBuilder(classDeclaration.aopProxyName())
            .addOriginatingKSFile(classDeclaration)
            .addTypeVariables(typeVariables)
            .superclass(if (typeVariables.isEmpty()) classDeclaration.toClassName() else classDeclaration.toClassName().parameterizedBy(typeVariables))
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addAnnotation(CommonClassNames.aopProxy)

        val reservedNames = constructor.parameters.map { it.name!!.asString() } + classDeclaration.getAllProperties().map { it.simpleName.asString() }
        val typeFieldFactory = TypeFieldFactory(resolver, reservedNames)
        val aopContext: KoraAspect.AspectContext = KoraAspect.AspectContext(typeBuilder, typeFieldFactory)

        classDeclaration.parseTag().let { tags ->
            if (tags != null) {
                typeBuilder.addAnnotation(tags.toTagAnnotation())
            }
        }
        if (classDeclaration.isAnnotationPresent(CommonClassNames.root)) {
            typeBuilder.addAnnotation(CommonClassNames.root)
        }

        val classFunctions = findMethods(classDeclaration) { f ->
            !f.isConstructor() && (f.isPublic() || f.isProtected() || f.isInternal())
        }

        val methodAspectsApplied = linkedSetOf<KoraAspect>()
        classFunctions.forEach { function ->
            val methodLevelTypeAspects = typeLevelAspects.toMutableList()
            val methodLevelAspects = mutableListOf<KoraAspect>()
            val methodParameterLevelAspects = mutableListOf<KoraAspect>()
            val functionAnnotations = function.annotations.toList()
            for (am in functionAnnotations) {
                val annotationType = am.annotationType.resolveToUnderlying().declaration.qualifiedName?.asString()
                    ?: continue
                aspects.forEach { aspect ->
                    val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
                    if (supportedAnnotationTypes.contains(annotationType)) {
                        if (!methodLevelAspects.contains(aspect)) {
                            methodLevelAspects.add(aspect)
                        }
                        methodLevelTypeAspects.remove(aspect)
                    }
                }
            }
            function.parameters.forEach { parameter ->
                for (am in parameter.annotations) {
                    val annotationType = am.annotationType.resolveToUnderlying().declaration.qualifiedName?.asString()
                        ?: continue

                    aspects.forEach { aspect ->
                        val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
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
            if (function.extensionReceiver != null) {
                throw ProcessingErrorException(extensionFunctionError(function), function)
            }
            KoraSymbolProcessingEnv.logger.logging(
                "Method level aspects for ${classDeclaration.qualifiedName!!.asString()}}#${function.simpleName.asString()}: {$methodLevelAspects}",
                classDeclaration
            )
            val aspectsToApply = methodLevelTypeAspects.toMutableList()
            aspectsToApply.addAll(methodLevelAspects)
            aspectsToApply.addAll(methodParameterLevelAspects)

            var superCall = "super." + function.simpleName.asString()
            val overridenMethod = FunSpec.builder(function.simpleName.asString())
                .addModifiers(KModifier.OVERRIDE)
            function.returnType?.resolve()?.let { overridenMethod.returns(it.toTypeName()) }

            if (function.modifiers.contains(Modifier.SUSPEND)) {
                overridenMethod.addModifiers(KModifier.SUSPEND)
            }
            function.typeParameters.forEach { typeParameter ->
                overridenMethod.addTypeVariable(typeParameter.toTypeVariableName())
            }
            function.parameters.forEach { parameter ->
                val paramSpec = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.resolve().toTypeName())
                if (parameter.isVararg) {
                    paramSpec.addModifiers(KModifier.VARARG)
                }
                overridenMethod.addParameter(paramSpec.build())
            }
            // aspects pass a vararg parameter on as an array, so the super call goes through a bridge that spreads it
            val superBridge = if (function.parameters.none { it.isVararg }) null else {
                FunSpec.builder("_" + function.simpleName.asString() + "_AopProxy_super")
                    .addModifiers(KModifier.PRIVATE)
                    .addTypeVariables(overridenMethod.typeVariables)
                    .addParameters(function.parameters.map { ParameterSpec(it.name!!.asString(), it.aopProxyParameterType()) })
                    .returns(function.returnType!!.resolve().toTypeName())
                    .addStatement("return super.%N(%L)", function.simpleName.asString(), function.parameters.joinToString { (if (it.isVararg) "*" else "") + it.name!!.asString() })
                    .apply { if (function.modifiers.contains(Modifier.SUSPEND)) addModifiers(KModifier.SUSPEND) }
                    .build()
                    .also { superCall = it.name }
            }

            aspectsToApply.reverse()
            val generatedMethodNames = mutableSetOf<String>()
            var isMethodAspectApplied = false
            for (aspect in aspectsToApply) {
                val result: KoraAspect.ApplyResult = aspect.apply(function, superCall, aopContext)
                if (result is KoraAspect.ApplyResult.Noop) {
                    continue
                }

                val methodBody: KoraAspect.ApplyResult.MethodBody = result as KoraAspect.ApplyResult.MethodBody
                val baseMethodName = "_" + function.simpleName.asString() + "_AopProxy_" + aspect::class.simpleName
                var methodName = baseMethodName
                if (!generatedMethodNames.add(methodName)) {
                    for (i in 0..Int.MAX_VALUE) {
                        methodName = baseMethodName + i
                        if (generatedMethodNames.add(methodName)) {
                            break
                        }
                    }
                }

                superCall = methodName
                val f = FunSpec.builder(methodName)
                    .addModifiers(KModifier.PRIVATE)
                    .addCode(methodBody.codeBlock)

                if (function.modifiers.contains(Modifier.SUSPEND)) {
                    f.addModifiers(KModifier.SUSPEND)
                }

                function.parameters.forEach { parameter ->
                    f.addParameter(parameter.name!!.asString(), parameter.aopProxyParameterType())
                }
                function.typeParameters.forEach { typeParameter ->
                    f.addTypeVariable(typeParameter.toTypeVariableName())
                }
                val returnType = function.returnType!!.resolve()
                f.returns(returnType.toTypeName())
                typeBuilder.addFunction(f.build())
                methodAspectsApplied.add(aspect)
                isMethodAspectApplied = true
            }

            if (isMethodAspectApplied) {
                superBridge?.let { typeBuilder.addFunction(it) }
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

            parameter.parseTag()?.let { tags ->
                parameterSpec.addAnnotation(tags.toTagAnnotation())
            }

            constructorBuilder.addParameter(parameterSpec.build())
        }
        typeFieldFactory.addFields(typeBuilder)
        typeFieldFactory.enrichConstructor(constructorBuilder)
        typeBuilder.primaryConstructor(constructorBuilder.build())
        return typeBuilder.build()
    }

    private fun extensionFunctionError(function: KSFunctionDeclaration): String {
        return """
            AOP aspect cannot be applied to extension function '${function.parentDeclaration?.qualifiedName?.asString()}#${function.simpleName.asString()}'.

            Fix: declare the receiver as a regular function parameter, or move the aspect to a function without a receiver.
        """.trimIndent()
    }

    private fun aopConstructorError(classDeclaration: KSClassDeclaration): String {
        return """
            AOP proxy cannot be generated for '${classDeclaration.qualifiedName?.asString()}': no suitable constructor was found.

            Fix: provide at least one public or protected constructor that can be called from the generated proxy. Private constructors and constructors with unsupported visibility cannot be used for AOP proxy generation.
        """.trimIndent()
    }
}
