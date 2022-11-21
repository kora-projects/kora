package ru.tinkoff.kora.config.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.typesafe.config.Config
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.config.common.extractor.ConfigValueUtils
import ru.tinkoff.kora.config.common.extractor.ObjectConfigValueExtractor
import ru.tinkoff.kora.config.ksp.exception.NewRoundWantedException
import ru.tinkoff.kora.ksp.common.KspCommonUtils.fixPlatformType
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import java.time.Duration
import java.time.Period
import java.time.temporal.TemporalAmount
import javax.annotation.processing.Generated

class ConfigParserGenerator(
    private val resolver: Resolver
) {

    private val log: Logger = LoggerFactory.getLogger(ConfigParserGenerator::class.java)
    private val configValueExtractorTypeDeclaration = resolver.getClassDeclarationByName(ConfigValueExtractor::class.qualifiedName!!)!!
    private val objectConfigValueExtractorTypeDeclaration = resolver.getClassDeclarationByName(ObjectConfigValueExtractor::class.qualifiedName!!)!!
    private val configObjectType = resolver.getClassDeclarationByName(ConfigObject::class.qualifiedName!!)!!.asStarProjectedType()
    private val configType = resolver.getClassDeclarationByName(Config::class.qualifiedName!!)!!.asStarProjectedType()
    private val configListType = resolver.getClassDeclarationByName(ConfigList::class.qualifiedName!!)!!.asStarProjectedType()
    private val durationType = resolver.getClassDeclarationByName(Duration::class.qualifiedName!!)!!.asStarProjectedType()
    private val periodType = resolver.getClassDeclarationByName(Period::class.qualifiedName!!)!!.asStarProjectedType()
    private val temporalAmountType = resolver.getClassDeclarationByName(TemporalAmount::class.qualifiedName!!)!!.asStarProjectedType()
    private var configValueUtilsTypeElement = resolver.getClassDeclarationByName(ConfigValueUtils::class.qualifiedName!!)!!.asStarProjectedType()

    fun generate(targetDeclaration: KSClassDeclaration): FileSpec {
        log.info("Generating ConfigValueExtractor for {}", targetDeclaration)
        val typeParameterResolver = targetDeclaration.typeParameters.toTypeParameterResolver()
        val packageName = targetDeclaration.packageName.asString()
        val typeName = targetDeclaration.getOuterClassesAsPrefix() + targetDeclaration.simpleName.getShortName() + "_" + ConfigValueExtractor::class.simpleName!!
        val fields = HashMap<TypeName, String>()
        val extractorType = objectConfigValueExtractorTypeDeclaration.toClassName().parameterizedBy(
            targetDeclaration.toClassName()
        )
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .superclass(extractorType)
            .addAnnotation(
                AnnotationSpec.builder(Generated::class).addMember("%S", ConfigParserGenerator::class.java.canonicalName).build()
            )
        val constructorBuilder = FunSpec.constructorBuilder()
        val methodBody = CodeBlock.builder()
            .add("return %T(", targetDeclaration.toClassName()).add("\n")
            .indent()
        val elementConstructor = getConstructor(targetDeclaration)
        val parameters = elementConstructor.parameters
        for (i in parameters.indices) {
            val param = parameters[i]
            val paramType = param.type.resolve()
            if (paramType.isError) {
                throw NewRoundWantedException(targetDeclaration)
            }
            val paramName = param.name!!.asString()
            val defaultExtractor = findDefaultExtractor(paramType)
            val trailingComma = if (i + 1 != parameters.size) "," else ""
            val isNullable = param.type.resolve().isMarkedNullable
            if (isNullable) {
                methodBody.add("if (!config.hasPath(%S)) null else ", paramName)
            }
            if (defaultExtractor != null) {
                methodBody.add("config.%L(%S)%L", defaultExtractor, paramName, trailingComma)
            } else {
                val fixedType = paramType.fixPlatformType(resolver)
                val fieldExtractor = fields.computeIfAbsent(fixedType.toTypeName(typeParameterResolver).copy(nullable = false)) {
                    val fieldName = "extractor" + fields.size
                    val paramExtractorTypeName = configValueExtractorTypeDeclaration.toClassName().parameterizedBy(
                        it
                    )
                    typeBuilder.addProperty(PropertySpec.builder(fieldName, paramExtractorTypeName, KModifier.PRIVATE).build())
                    constructorBuilder
                        .addParameter(ParameterSpec.builder(fieldName, paramExtractorTypeName).build())
                        .addStatement("this.%L = %L", fieldName, fieldName)
                    fieldName
                }
                if (isNullable) {
                    methodBody.add("%L.extract(config.getValue(%S))%L", fieldExtractor, paramName, trailingComma)
                } else {
                    methodBody.add("%L.extract(%T.getValueOrNull(config, %S))%L", fieldExtractor, configValueUtilsTypeElement.toTypeName(), paramName, trailingComma)
                }
            }
            methodBody.add("\n")
        }
        methodBody.unindent().addStatement(")")
        val type = typeBuilder
            .primaryConstructor(constructorBuilder.build())
            .addFunction(
                FunSpec.builder("extract")
                    .addModifiers(KModifier.PROTECTED, KModifier.OVERRIDE)
                    .returns(targetDeclaration.toClassName())
                    .addParameter("config", Config::class.java.asTypeName())
                    .addCode(methodBody.build())
                    .build()
            )
            .build()
        return FileSpec.builder(packageName, type.name!!).addType(type).build()
    }

    private fun getConstructor(declaration: KSClassDeclaration): KSFunctionDeclaration {
        return declaration.getConstructors().first()
    }

    private fun findDefaultExtractor(parameterType: KSType): String? {
        return when (parameterType.makeNotNullable()) {
            resolver.builtIns.booleanType -> "getBoolean"
            resolver.builtIns.numberType -> "getNumber"
            resolver.builtIns.intType -> "getInt"
            resolver.builtIns.longType -> "getLong"
            resolver.builtIns.doubleType -> "getDouble"
            resolver.builtIns.stringType -> "getString"
            configObjectType -> "getObject"
            configType -> "getConfig"
            configListType -> "getList"
            durationType -> "getDuration"
            periodType -> "getPeriod"
            temporalAmountType -> "getTemporal"
            else -> null
        }
    }


}
