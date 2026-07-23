package io.koraframework.logging.symbol.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.CommonClassNames.isCollection
import io.koraframework.ksp.common.CommonClassNames.isMap
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.getNameConverter
import io.koraframework.ksp.common.generatedClassName

class MaskingRulesSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    private val processed = HashSet<String>()
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val delayed = ArrayList<KSAnnotated>()
        for (symbol in resolver.getSymbolsWithAnnotation(LoggingTypes.mask.canonicalName)) {
            if (!symbol.validateAll()) {
                delayed.add(symbol)
                continue
            }
            if (symbol !is KSClassDeclaration) {
                continue
            }
            if (symbol.modifiers.contains(Modifier.ABSTRACT)) {
                kspLogger.error("Abstract classes can't be annotated with @Mask", symbol)
                continue
            }
            if (!processed.add(symbol.qualifiedName!!.asString())) {
                continue
            }
            MaskingRulesGenerator(resolver, codeGenerator).generate(symbol)
        }
        return delayed
    }
}

private class MaskingRulesGenerator(
    private val resolver: Resolver,
    private val codeGenerator: CodeGenerator
) {
    fun generate(root: KSClassDeclaration) {
        val packageName = root.packageName.asString()
        val className = root.generatedClassName("MaskingRulesModule")
        if (resolver.getClassDeclarationByName(resolver.getKSNameFromString("$packageName.$className")) != null) {
            return
        }

        val rules = ArrayList<MaskingRuleMeta>()
        visit(root, emptyList(), HashSet(), rules)
        val strategies = strategies(rules)

        val factoryMethod = FunSpec.builder(root.simpleName.asString().replaceFirstChar { it.lowercase() } + "MaskingRules")
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(CommonClassNames.defaultComponent)
            .returns(LoggingTypes.maskingRules.parameterizedBy(root.toClassName()))
        for (strategy in strategies.values) {
            factoryMethod.addParameter(strategy.parameterName, strategy.type.toClassName())
        }
        factoryMethod.addStatement("return %L", rulesCode(root, rules, strategies))

        val type = TypeSpec.interfaceBuilder(className)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .generated(MaskingRulesGenerator::class)
            .addOriginatingKSFile(root)
            .addFunction(factoryMethod.build())
            .build()

        FileSpec.builder(packageName, className)
            .addType(type)
            .build()
            .writeTo(codeGenerator = codeGenerator, aggregating = false)
    }

    private fun rulesCode(root: KSClassDeclaration, rules: List<MaskingRuleMeta>, strategies: Map<String, StrategyMeta>): CodeBlock {
        val code = CodeBlock.builder().add("%T.builder(%T::class.java)", LoggingTypes.maskingRules, root.toClassName())
        for (rule in rules) {
            val strategy = strategies[rule.strategy.qualifiedName!!.asString()]!!
            code.add("\n.mask(%S, %N)", rule.path.joinToString("."), strategy.parameterName)
        }
        return code.add("\n.build()").build()
    }

    private fun strategies(rules: List<MaskingRuleMeta>): Map<String, StrategyMeta> {
        val strategies = linkedMapOf<String, StrategyMeta>()
        for (rule in rules) {
            val key = rule.strategy.qualifiedName!!.asString()
            strategies.computeIfAbsent(key) {
                StrategyMeta(rule.strategy, "strategy${strategies.size}")
            }
        }
        return strategies
    }

    private fun visit(type: KSClassDeclaration, path: List<String>, branch: MutableSet<String>, rules: MutableList<MaskingRuleMeta>) {
        if (!type.isJsonOrMasked()) {
            return
        }
        val key = type.qualifiedName!!.asString()
        if (!branch.add(key)) {
            return
        }
        val typeMask = type.findAnnotation(LoggingTypes.mask)
        for (field in parse(type)) {
            val fieldPath = path + field.jsonName
            val mask = field.mask
            if (mask != null) {
                rules.add(MaskingRuleMeta(fieldPath, maskStrategy(mask, typeMask), false))
                continue
            }
            visitFieldType(field.type, fieldPath, branch, rules)
        }
        branch.remove(key)
    }

    private fun visitFieldType(type: KSType, path: List<String>, branch: MutableSet<String>, rules: MutableList<MaskingRuleMeta>) {
        if (type.isCollection()) {
            val argument = type.arguments.firstOrNull()?.type?.resolve() ?: return
            visitFieldType(argument, path, branch, rules)
            return
        }
        if (type.isMap()) {
            val argument = type.arguments.getOrNull(1)?.type?.resolve() ?: return
            visitFieldType(argument, path + "*", branch, rules)
            return
        }
        val declaration = type.declaration
        if (declaration is KSClassDeclaration) {
            visit(declaration, path, branch, rules)
        }
    }

    private fun maskStrategy(mask: KSAnnotation, typeMask: KSAnnotation?): KSClassDeclaration {
        val strategy = mask.findValueNoDefault<KSType>("value")
            ?: typeMask?.findValue<KSType>("value")
            ?: resolver.getClassDeclarationByName(resolver.getKSNameFromString(LoggingTypes.maskingFull.canonicalName))!!.asStarProjectedType()
        return strategy.declaration as KSClassDeclaration
    }

    private fun parse(type: KSClassDeclaration): List<MaskingField> {
        val nameConverter = type.getNameConverter()
        val fields = ArrayList<MaskingField>()
        for (property in type.getAllProperties()) {
            if (property.isAnnotationPresent(LoggingTypes.jsonSkip)) {
                continue
            }
            val constructorParameter = type.primaryConstructor?.parameters?.firstOrNull { it.name?.asString() == property.simpleName.asString() }
            val jsonField = property.findAnnotation(LoggingTypes.jsonField) ?: constructorParameter?.findAnnotation(LoggingTypes.jsonField)
            val jsonName = jsonField?.findValueNoDefault<String>("value")?.takeIf { it.isNotBlank() }
                ?: nameConverter?.convert(property.simpleName.asString())
                ?: property.simpleName.asString()
            val mask = property.findAnnotation(LoggingTypes.mask) ?: constructorParameter?.findAnnotation(LoggingTypes.mask)
            fields.add(MaskingField(jsonName, property.type.resolve(), mask))
        }
        return fields
    }

    private fun KSClassDeclaration.isJsonOrMasked(): Boolean {
        return this.isAnnotationPresent(LoggingTypes.json)
            || this.isAnnotationPresent(LoggingTypes.jsonWriter)
            || this.isAnnotationPresent(LoggingTypes.mask)
    }
}

private object LoggingTypes {
    val mask = ClassName("io.koraframework.logging.common.annotation", "Mask")
    val json = ClassName("io.koraframework.json.common.annotation", "Json")
    val jsonWriter = ClassName("io.koraframework.json.common.annotation", "JsonWriter")
    val jsonField = ClassName("io.koraframework.json.common.annotation", "JsonField")
    val jsonSkip = ClassName("io.koraframework.json.common.annotation", "JsonSkip")
    val maskingRules = ClassName("io.koraframework.logging.common.masking", "MaskingRules")
    val maskingFull = ClassName("io.koraframework.logging.common.masking", "MaskingFull")
}

private data class MaskingField(val jsonName: String, val type: KSType, val mask: KSAnnotation?)

private data class MaskingRuleMeta(val path: List<String>, val strategy: KSClassDeclaration, val fieldOnly: Boolean)

private data class StrategyMeta(val type: KSClassDeclaration, val parameterName: String)
