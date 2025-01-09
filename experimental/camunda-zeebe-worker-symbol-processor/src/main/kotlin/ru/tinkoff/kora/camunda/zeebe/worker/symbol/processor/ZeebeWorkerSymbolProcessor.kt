package ru.tinkoff.kora.camunda.zeebe.worker.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.generatedClassName
import java.io.IOException
import java.io.UncheckedIOException

class ZeebeWorkerSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    companion object {
        private val ANNOTATION_WORKER: ClassName = ClassName("ru.tinkoff.kora.camunda.zeebe.worker.annotation", "JobWorker")

        private val ANNOTATION_VARIABLE: ClassName = ClassName("ru.tinkoff.kora.camunda.zeebe.worker.annotation", "JobVariable")
        private val ANNOTATION_VARIABLES: ClassName = ClassName("ru.tinkoff.kora.camunda.zeebe.worker.annotation", "JobVariables")

        private val CLASS_KORA_WORKER: ClassName = ClassName("ru.tinkoff.kora.camunda.zeebe.worker", "KoraJobWorker")
        private val CLASS_JOB_CONTEXT: ClassName = ClassName("ru.tinkoff.kora.camunda.zeebe.worker", "JobContext")
        private val CLASS_ACTIVE_CONTEXT: ClassName = ClassName("ru.tinkoff.kora.camunda.zeebe.worker", "ActiveJobContext")
        private val CLASS_CLIENT: ClassName = ClassName("io.camunda.zeebe.client.api.worker", "JobClient")
        private val CLASS_FINAL_COMMAND: ClassName = ClassName("io.camunda.zeebe.client.api.command", "FinalCommandStep")
        private val CLASS_ACTIVE_JOB: ClassName = ClassName("io.camunda.zeebe.client.api.response", "ActivatedJob")
        private val CLASS_WORKER_EXCEPTION: ClassName = ClassName("ru.tinkoff.kora.camunda.zeebe.worker", "JobWorkerException")
        private val CLASS_JSON_READER: ClassName = ClassName("ru.tinkoff.kora.json.common", "JsonReader")
        private val CLASS_JSON_WRITER: ClassName = ClassName("ru.tinkoff.kora.json.common", "JsonWriter")
        private val CLASS_VARIABLE_READER = ClassName("ru.tinkoff.kora.camunda.zeebe.worker", "ZeebeVariableJsonReader")
        private val CLASS_WORKER_CONFIG: ClassName = ClassName("ru.tinkoff.kora.camunda.zeebe.worker", "ZeebeWorkerConfig")
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_WORKER.canonicalName).toList()
        val symbolsToProcess = symbols.filter { it.validate() }.filterIsInstance<KSFunctionDeclaration>()
        for (method in symbolsToProcess) {
            if (method.modifiers.any { m -> m == Modifier.PRIVATE }) {
                throw ProcessingErrorException("@JobWorker method can't be private", method)
            }
            if (method.modifiers.any { m -> m == Modifier.SUSPEND }) {
                throw ProcessingErrorException("@JobWorker method can't be suspend", method)
            }

            val packageName = method.packageName.asString()
            val ownerType = getOwner(method)
            val variables = getVariables(method)

            val implSpecBuilder = TypeSpec.classBuilder(ownerType.generatedClassName("${method.simpleName.asString()}_KoraJobWorker"))
                .generated(ZeebeWorkerSymbolProcessor::class)
                .addAnnotation(CommonClassNames.component)
                .addSuperinterface(CLASS_KORA_WORKER)

            val methodConstructor = getMethodConstructor(ownerType, method, implSpecBuilder, variables)

            val methodFetchVariables = getMethodFetchVariables(method, variables)
            if (methodFetchVariables != null) {
                implSpecBuilder.addFunction(methodFetchVariables)
            }

            val spec = implSpecBuilder
                .addFunction(methodConstructor)
                .addFunction(getMethodType(method))
                .addFunction(getMethodHandler(ownerType, method, variables))
                .build()

            val fileModuleSpec = FileSpec.builder(packageName, spec.name.toString())
                .addType(spec)
                .build()
            fileModuleSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)
        }

        return symbols.filterNot { it.validate() }.toList()
    }

    private fun getJobType(method: KSFunctionDeclaration): String {
        val ann = method.findAnnotation(ANNOTATION_WORKER)
        return ann!!.findValue("value") ?: throw ProcessingErrorException("Couldn't find JobType", method)
    }

    private fun getMethodHandler(
        ownerType: KSClassDeclaration,
        method: KSFunctionDeclaration,
        variables: List<Variable>
    ): FunSpec {
        val methodBuilder = FunSpec.builder("handle")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("client", CLASS_CLIENT)
            .addParameter("job", CLASS_ACTIVE_JOB)
            .returns(CLASS_FINAL_COMMAND.parameterizedBy(WildcardTypeName.producerOf(Any::class)))

        val codeBuilder = CodeBlock.builder()

        codeBuilder.beginControlFlow("try")
        val vars: MutableList<String> = ArrayList()

        var varCounter = 1
        for (variable in variables) {
            val varName = "var" + vars.size + 1
            vars.add(varName)
            if (variable.isVars) {
                codeBuilder.addStatement("val %L = varsReader.read(job.getVariables())", varName)
            } else if (variable.isContext) {
                codeBuilder.addStatement("val %L = %T(jobName, job)", varName, CLASS_ACTIVE_CONTEXT)
            } else if (variable.isVar) {
                val varReaderName = "var" + varCounter++ + "Reader"
                codeBuilder.addStatement("val %L = %L.read(job.getVariables())", varName, varReaderName)
            }
        }

        val methodName = method.simpleName.asString()
        val varsArg = java.lang.String.join(", ", vars)
        if (method.isVoid()) {
            codeBuilder.addStatement("this.handler.%L(%L)", methodName, varsArg)
            codeBuilder.addStatement("return client.newCompleteCommand(job)")
        } else {
            codeBuilder.addStatement("val result = this.handler.%L(%L)", methodName, varsArg)
            if (method.returnType!!.resolve().isMarkedNullable) {
                codeBuilder.beginControlFlow("if(result != null)")
                codeBuilder.addStatement("return client.newCompleteCommand(job).variables(varsWriter.toStringUnchecked(result))")
                codeBuilder.nextControlFlow("else")
                codeBuilder.addStatement("return client.newCompleteCommand(job)")
                codeBuilder.endControlFlow()
            } else {
                codeBuilder.addStatement("return client.newCompleteCommand(job).variables(varsWriter.toStringUnchecked(result))")
            }
        }

        if (variables.any { v -> v.isVar || v.isVars }) {
            codeBuilder.nextControlFlow("catch (e: %T)", IOException::class.java)
            codeBuilder.addStatement("throw %T(%S, e)", CLASS_WORKER_EXCEPTION, "DESERIALIZATION")
        }

        if (!method.isVoid()) {
            codeBuilder.nextControlFlow("catch (e: %T)", UncheckedIOException::class.java)
            codeBuilder.addStatement("throw %T(%S, e)", CLASS_WORKER_EXCEPTION, "SERIALIZATION")
        }

        codeBuilder.nextControlFlow("catch (e: %T)", CLASS_WORKER_EXCEPTION)
        codeBuilder.addStatement("throw e")
        codeBuilder.nextControlFlow("catch (e: Exception)", CLASS_WORKER_EXCEPTION)
        codeBuilder.addStatement("throw %T(%S, e)", CLASS_WORKER_EXCEPTION, "UNEXPECTED")
        codeBuilder.endControlFlow()

        return methodBuilder
            .addCode(codeBuilder.build())
            .build()
    }

    private fun getMethodFetchVariables(method: KSFunctionDeclaration, variables: List<Variable>): FunSpec? {
        if (variables.none { v -> v.isVar }) {
            return null
        }

        val varArg = variables
            .filter { v -> v.isVar }
            .map { v -> "\"${v.name}\"" }
            .joinToString(", ")

        return FunSpec.builder("fetchVariables")
            .addModifiers(KModifier.OVERRIDE)
            .returns(MutableList::class.parameterizedBy(String::class))
            .addStatement("return %M(%L)", MemberName("kotlin.collections", "listOf"), varArg)
            .build()
    }

    private fun getMethodType(method: KSFunctionDeclaration): FunSpec {
        val jobType: String = getJobType(method)

        return FunSpec.builder("type")
            .addModifiers(KModifier.OVERRIDE)
            .returns(String::class)
            .addStatement("return %S", jobType)
            .build()
    }

    private fun getMethodConstructor(
        ownerType: KSClassDeclaration,
        method: KSFunctionDeclaration,
        implBuilder: TypeSpec.Builder,
        variables: List<Variable>
    ): FunSpec {
        val methodBuilder = FunSpec.constructorBuilder()
        val constructorBuilder = CodeBlock.builder()

        val handlerTypeName = ownerType.toTypeName()
        implBuilder.addProperty("handler", handlerTypeName, KModifier.PRIVATE, KModifier.FINAL)
        methodBuilder.addParameter("handler", handlerTypeName)
        constructorBuilder.addStatement("this.handler = handler")

        if (variables.any { v -> v.isContext }) {
            implBuilder.addProperty("jobName", String::class, KModifier.PRIVATE, KModifier.FINAL)
            methodBuilder.addParameter("config", CLASS_WORKER_CONFIG)
            constructorBuilder.addStatement("this.jobName = config.getJobConfig(%S).name()", getJobType(method))
        }

        if (method.isMono() || method.isFlux() || method.isFuture()) {
            throw ProcessingErrorException("@JobWorker return type can't be Mono/Flux/CompletionStage/Suspend", method)
        } else if (!method.isVoid()) {
            val returnType = method.returnType
            val writerType = CLASS_JSON_WRITER.parameterizedBy(returnType!!.toTypeName())
            implBuilder.addProperty("varsWriter", writerType, KModifier.PRIVATE, KModifier.FINAL)
            methodBuilder.addParameter("varsWriter", writerType)
            constructorBuilder.addStatement("this.varsWriter = varsWriter")
        }

        variables
            .firstOrNull { v -> v.isVars }
            ?.let { vars ->
                val readerType = CLASS_JSON_READER.parameterizedBy(vars.parameter.type.toTypeName())
                implBuilder.addProperty("varsReader", readerType, KModifier.PRIVATE, KModifier.FINAL)
                methodBuilder.addParameter("varsReader", readerType)
                constructorBuilder.addStatement("this.varsReader = varsReader")
            }

        var varCounter = 1
        for ((parameter, name, isVar) in variables) {
            if (isVar) {
                val readerType = CLASS_JSON_READER.parameterizedBy(parameter.type.toTypeName())
                val readerName = "var" + varCounter + "Reader"
                implBuilder.addProperty(readerName, readerType, KModifier.PRIVATE, KModifier.FINAL)
                methodBuilder.addParameter(readerName, readerType)
                val isNullable = parameter.type.resolve().isMarkedNullable
                constructorBuilder.addStatement("this.%L = %T(%S, %L, %L)", readerName, CLASS_VARIABLE_READER, name, isNullable, readerName)
                varCounter++
            }
        }

        return methodBuilder
            .addCode(constructorBuilder.build())
            .build()
    }

    private fun getVariables(method: KSFunctionDeclaration): List<Variable> {
        val variables: MutableList<Variable> = ArrayList()
        var haveAlreadyVars = false
        var haveAlreadyContext = false
        for (parameter in method.parameters) {
            val isVars = parameter.findAnnotation(ANNOTATION_VARIABLES) != null
            if (isVars) {
                if (haveAlreadyVars) {
                    throw ProcessingErrorException(
                        "One @${ANNOTATION_VARIABLES.simpleName} variable only can be specified", parameter
                    )
                } else {
                    haveAlreadyVars = true
                }
            }

            val isContext = parameter.type.toTypeName() == CLASS_JOB_CONTEXT
            if (isContext) {
                if (haveAlreadyContext) {
                    throw ProcessingErrorException(
                        "One @%s variable only can be specified ${ANNOTATION_VARIABLES.simpleName}", parameter
                    )
                } else {
                    haveAlreadyContext = true
                }
            }

            val varAnnotation = parameter.findAnnotation(ANNOTATION_VARIABLE)
            val isVar = varAnnotation != null
            if (isVars || isContext || isVar) {
                val varName = if (isVar)
                    varAnnotation!!.findValueNoDefault<String>("value") ?: parameter.name!!.asString()
                else
                    parameter.name!!.asString()

                variables.add(Variable(parameter, varName, isVar, isVars, isContext))
            } else {
                throw ProcessingErrorException(
                    "Only @${ANNOTATION_VARIABLES.simpleName} and @${ANNOTATION_VARIABLE.simpleName} and ${CLASS_JOB_CONTEXT.simpleName} variables are supported as JobWorker arguments", method
                )
            }
        }

        return variables
    }

    data class Variable(
        val parameter: KSValueParameter,
        val name: String,
        val isVar: Boolean,
        val isVars: Boolean,
        val isContext: Boolean
    )

    private fun getOwner(method: KSFunctionDeclaration): KSClassDeclaration {
        var enclosingElement = method.parentDeclaration

        while (enclosingElement != null) {
            if (enclosingElement is KSClassDeclaration) {
                return enclosingElement
            }

            enclosingElement = enclosingElement.parentDeclaration
        }

        throw ProcessingErrorException("Can't find KSClassDeclaration for " + method.simpleName, method)
    }
}

