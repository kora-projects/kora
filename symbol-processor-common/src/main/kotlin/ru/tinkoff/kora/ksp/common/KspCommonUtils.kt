package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSEmptyVisitor
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotations
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import kotlin.reflect.KClass


object KspCommonUtils {

    fun KSAnnotated.findRepeatableAnnotation(annotationName: ClassName, containerName: ClassName): List<KSAnnotation> {
        val annotations = this.findAnnotations(annotationName)
        val containeredAnnotations = this.findAnnotations(containerName)
            .flatMap { it.arguments.asSequence().filter { it.name?.asString() == "value" } }
            .flatMap { it.value as ArrayList<*> }
            .map { it as KSAnnotation }

        return sequenceOf(annotations, containeredAnnotations).flatten().toList()
    }

    fun KSType.fixPlatformType(resolver: Resolver): KSType {
        val type = if (this.nullability == Nullability.PLATFORM) {
            this.makeNotNullable()
        } else {
            this
        }
        val fixMutability = type.toString().startsWith("(Mutable")
        if (this.arguments.isEmpty()) {
            if (fixMutability) {
                return type.immutableDeclaration(resolver).asType(listOf())
            } else {
                return type
            }
        }
        var changed = false
        val args = ArrayList<KSTypeArgument>(this.arguments.size)
        for (arg in this.arguments) {
            val argType = arg.type!!.resolve()
            val newArgType = argType.fixPlatformType(resolver)
            if (newArgType !== argType) {
                changed = true
                args.add(
                    resolver.getTypeArgument(
                        resolver.createKSTypeReferenceFromKSType(newArgType),
                        arg.variance
                    )
                )
            } else {
                args.add(arg)
            }
        }
        if (changed) {
            if (fixMutability) {
                return type.immutableDeclaration(resolver).asType(args)
            } else {
                return type.replace(args)
            }
        } else {
            if (fixMutability) {
                return type.immutableDeclaration(resolver).asType(listOf())
            } else {
                return type
            }
        }
    }


    private fun KSType.immutableDeclaration(resolver: Resolver): KSClassDeclaration {
        val declaration = this.declaration
        val immutableName = declaration.qualifiedName!!.asString().replaceFirst(".Mutable", ".")
        return resolver.getClassDeclarationByName(resolver.getKSNameFromString(immutableName))!!
    }

    fun TypeSpec.Builder.generated(generator: KClass<*>) = addAnnotation(AnnotationSpec.builder(CommonClassNames.generated).addMember("%S", generator.java.canonicalName).build())

    fun TypeSpec.Builder.generated(generated: List<KClass<*>>) {
        val generatedValue = generated.asSequence()
            .map { CodeBlock.of("%S", it.qualifiedName) }
            .joinToString(", ", "value = [", "]")

        addAnnotation(
            AnnotationSpec.builder(CommonClassNames.generated)
                .addMember(generatedValue).build()
        )
    }

    fun KSFunctionDeclaration.parametrized(returnType: KSType, parameterTypes: List<KSType>): KSFunction {
        val typeParameters = this.typeParameters
        return object : KSFunction {
            override val returnType = returnType
            override val typeParameters = typeParameters
            override val parameterTypes = parameterTypes

            override val extensionReceiverType get() = TODO("Not yet implemented")
            override val isError get() = TODO("Not yet implemented")
        }
    }

    fun KSClassDeclaration.toTypeName(): TypeName {
        val className = this.toClassName()
        if (this.typeParameters.isEmpty()) {
            return className
        }
        val resolver = this.typeParameters.toTypeParameterResolver()
        val typeVariables = this.typeParameters.map { it.toTypeVariableName(resolver) }
        return className.parameterizedBy(typeVariables)
    }

    fun KSClassDeclaration.toTypeName(typeParameters: List<TypeName>): TypeName {
        val className = this.toClassName()
        if (this.typeParameters.isEmpty()) {
            return className
        }
        return className.parameterizedBy(typeParameters)
    }

    fun KSClassDeclaration.collectFinalSealedSubtypes(): Sequence<KSClassDeclaration> = this.getSealedSubclasses()
        .flatMap { if (it.modifiers.contains(Modifier.SEALED)) it.collectFinalSealedSubtypes() else sequenceOf(it) }

    fun Resolver.getClassDeclarationByName(cn: ClassName) = this.getClassDeclarationByName(cn.canonicalName)

    fun TypeSpec.Builder.addOriginatingKSFile(kDecl: KSDeclaration): TypeSpec.Builder {
        kDecl.containingFile?.let { this.addOriginatingKSFile(it) }
        return this
    }

    fun KSTypeReference.resolveToUnderlying(): KSType {
        var candidate = resolve()
        var declaration = candidate.declaration
        while (declaration is KSTypeAlias) {
            candidate = declaration.type.resolve()
            declaration = candidate.declaration
        }
        return candidate
    }


    fun KSDeclaration.getNamingStrategyConverterClass() = this.findAnnotation(CommonClassNames.namingStrategy)
        ?.findValueNoDefault<KSType>("value")
        ?.declaration
        ?.qualifiedName
        ?.asString()
        ?.let { Class.forName(it) }

    fun interface NameConverter {
        fun convert(originalName: String): String
    }

    fun <T: NameConverter> KSDeclaration.getNameConverter(defaultValue: T) = getNameConverter() ?: defaultValue

    fun KSDeclaration.getNameConverter(): NameConverter? {
        val namingStrategyClass = getNamingStrategyConverterClass()
        if (namingStrategyClass == null) {
            return null
        }
        try {
            val instance = namingStrategyClass.getConstructor().newInstance()
            val method = instance.javaClass.getMethod("convert", String::class.java)
            return object : NameConverter {
                override fun convert(originalName: String) = method.invoke(instance, originalName) as String
            }
        } catch (e: Exception) {
            throw ProcessingErrorException("Error on calling name converter constructor " + this + ": " + e.message, this)
        }
    }

}


data class MappersData(val mapperClasses: List<KSType>, val tag: String?) {
    fun getMapping(type: KSType): MappingData? {
        if (mapperClasses.isEmpty() && tag == null) {
            return null
        }
        for (mapperClass in mapperClasses) {
            if (type.isAssignableFrom(mapperClass)) {
                return MappingData(mapperClass, tag)
            }
        }
        if (tag == null) {
            return null
        } else {
            return MappingData(null, tag)
        }
    }

    fun getMapping(type: ClassName): MappingData? {
        if (mapperClasses.isEmpty() && tag == null) {
            return null
        }
        for (mapperClass in mapperClasses) {
            val declaration = mapperClass.declaration
            if (declaration !is KSClassDeclaration) {
                continue
            }
            if (declaration.doesImplement(type)) {
                return MappingData(mapperClass, tag)
            }
        }
        if (tag == null) {
            return null
        } else {
            return MappingData(null, tag)
        }
    }

}

fun KSClassDeclaration.doesImplement(type: ClassName) = this.toClassName() == type || this.superTypes
    .any { (it.resolve().declaration as KSClassDeclaration).toClassName() == type }


data class MappingData(val mapper: KSType?, val tag: String?) {
    fun toTagAnnotation() = this.tag?.toTagAnnotation()

    fun isGeneric() = mapper != null && mapper.declaration.typeParameters.isNotEmpty()

    fun parameterized(tn: TypeName): TypeName {
        assert(isGeneric())
        return mapper!!.toClassName().parameterizedBy(tn)
    }
}

fun KSAnnotated.parseMappingData(): MappersData {
    val tags = TagUtils.parseTagValue(this)
    val mappingsAnnotation = this.findAnnotation(CommonClassNames.mappings)
    if (mappingsAnnotation != null) {
        val mappings = mappingsAnnotation.findValue<List<KSAnnotation>>("value")!!
        val mappers = mappings.map { it.findValue<KSType>("value")!! }
        return MappersData(mappers, tags)
    }
    val mappers = parseAnnotationClassValue(this, CommonClassNames.mapping.canonicalName)
    return MappersData(mappers, tags)
}

fun <T> KSAnnotated.visitClass(visitor: (KSClassDeclaration) -> T) = this.accept(object : KSEmptyVisitor<Any?, T?>() {
    override fun defaultHandler(node: KSNode, data: Any?): T? = null

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Any?): T? {
        return visitor(classDeclaration)
    }
}, null)

fun <T> KSAnnotated.visitFunction(visitor: (KSFunctionDeclaration) -> T) = this.accept(object : KSEmptyVisitor<Any?, T?>() {
    override fun defaultHandler(node: KSNode, data: Any?): T? = null

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Any?): T? {
        return visitor(function)
    }
}, null)

fun <T> KSAnnotated.visitFunctionArgument(visitor: (KSValueParameter) -> T) = this.accept(object : KSEmptyVisitor<Any?, T?>() {
    override fun defaultHandler(node: KSNode, data: Any?): T? = null

    override fun visitValueParameter(valueParameter: KSValueParameter, data: Any?): T? {
        return visitor(valueParameter)
    }
}, null)

fun parseAnnotationClassValue(target: KSAnnotated, annotationName: String): List<KSType> {
    val annotation = target.annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName!!.asString() == annotationName }
    val arguments = annotation?.arguments ?: listOf()
    return arguments.asSequence()
        .filter { it.name!!.asString() == "value" }
        .map {
            if (it.value is KSType) {
                sequenceOf(it.value as KSType)
            } else {
                (it.value as ArrayList<*>).asSequence().map { it as KSType }
            }
        }
        .flatten()
        .toList()
}


inline fun <reified T> parseAnnotationValue(target: KSAnnotation, name: String) = target.findValue<T>(name)

fun parseTag(target: KSAnnotated): List<KSType> {
    return parseAnnotationClassValue(target, CommonClassNames.tag.canonicalName)
}

fun findMethods(ksAnnotated: KSAnnotated, functionFilter: (KSFunctionDeclaration) -> Boolean): List<KSFunctionDeclaration> {
    if (ksAnnotated !is KSClassDeclaration) {
        return emptyList()
    }
    val result = ArrayList<KSFunctionDeclaration>()
    for (function in ksAnnotated.getDeclaredFunctions().toList()) {
        if (!functionFilter(function)) {
            continue
        }
        result.add(function)
    }
    return result
}

fun KSAnnotated.getOuterClassesAsPrefix(): String {
    val prefix = StringBuilder("$")
    var parent = this.parent
    while (parent != null && parent is KSClassDeclaration) {
        prefix.insert(1, parent.simpleName.asString() + "_")
        parent = parent.parent
    }
    return prefix.toString()
}

fun KSDeclaration.generatedClass(suffix: String): String {
    return this.getOuterClassesAsPrefix() + this.simpleName.asString() + "_" + suffix
}

fun KSDeclaration.generatedClass(generatedType: ClassName): String {
    return this.getOuterClassesAsPrefix() + this.simpleName.asString() + "_" + generatedType.simpleName
}

fun KSClassDeclaration.generatedClassName(postfix: String): String {
    val prefix = StringBuilder("$")
    var parent = this.parent
    while (parent != null && parent is KSClassDeclaration) {
        prefix.insert(1, parent.simpleName.asString() + "_")
        parent = parent.parent
    }
    return prefix.toString() + this.simpleName.asString() + "_" + postfix
}

fun <T> measured(name: String, thunk: () -> T): T {
    val start = System.currentTimeMillis()
    try {
        return thunk().also {
            KoraSymbolProcessingEnv.logger.logging("$name processed in ${System.currentTimeMillis() - start}")
        }
    } catch (e: Exception) {
        KoraSymbolProcessingEnv.logger.exception(Exception("$name processed in ${System.currentTimeMillis() - start}", e))
        throw e
    }
}

fun KSClassDeclaration.isJavaRecord(): Boolean {
    return (this.origin == Origin.JAVA || this.origin == Origin.JAVA_LIB) && this.superTypes.any { it.resolve().declaration.qualifiedName?.asString() == "java.lang.Record" }
}

