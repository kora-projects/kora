package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.slf4j.LoggerFactory
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraRepositoryGenerator
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcRepositoryGenerator
import ru.tinkoff.kora.database.symbol.processor.r2dbc.R2DbcRepositoryGenerator
import ru.tinkoff.kora.database.symbol.processor.vertx.VertxRepositoryGenerator
import ru.tinkoff.kora.kora.app.ksp.extendsKeepAop
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

@KspExperimental
class RepositoryBuilder(
    val resolver: Resolver,
    private val kspLogger: KSPLogger
) {
    private val availableGenerators = listOf(
        JdbcRepositoryGenerator(resolver),
        VertxRepositoryGenerator(resolver, kspLogger),
        R2DbcRepositoryGenerator(resolver),
        CassandraRepositoryGenerator(resolver),
    )
    private val log = LoggerFactory.getLogger(RepositoryBuilder::class.java)


    fun build(repositoryDeclaration: KSClassDeclaration): TypeSpec? {
        log.info("Generating Repository for {}", repositoryDeclaration.simpleName.asString())
        val name = repositoryDeclaration.getOuterClassesAsPrefix() + repositoryDeclaration.simpleName.asString() + "_Impl"
        val builder = extendsKeepAop(repositoryDeclaration, resolver, name)
            .addOriginatingKSFile(repositoryDeclaration.containingFile!!)
        val constructorBuilder = FunSpec.constructorBuilder()
        if (repositoryDeclaration.classKind == ClassKind.CLASS) {
            this.enrichConstructorFromParentClass(builder, constructorBuilder, repositoryDeclaration)
        }
        val repositoryType = repositoryDeclaration.asType(listOf())
        for (availableGenerator in this.availableGenerators) {
            val repositoryInterface = availableGenerator.repositoryInterface()
            if (repositoryInterface != null && repositoryInterface.isAssignableFrom(repositoryType)) {
                return availableGenerator.generate(repositoryDeclaration, builder, constructorBuilder)
            }
        }
        throw ProcessingErrorException("Element doesn't extend any of known repository interfaces", repositoryDeclaration)
    }

    private fun enrichConstructorFromParentClass(builder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder, repositoryDeclaration: KSClassDeclaration) {
        val constructors = repositoryDeclaration.getConstructors().filter { !it.isProtected() }.toList()
        if (constructors.isEmpty()) {
            return
        }
        if (constructors.size > 1) {
            throw ProcessingErrorException("Abstract repository class has more than one public constructor", repositoryDeclaration)
        }
        val constructor = constructors[0]
        val parameters = constructor.parameters
        for (i in parameters.indices) {
            val parameter = parameters[i]
            val constructorParameter = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.toTypeName())
            for (annotation in parameter.annotations) {
                val annotationSpec = AnnotationSpec.builder(annotation.annotationType.resolve().toClassName())
                annotation.arguments.forEach { annotationArg ->
                    annotationSpec.addMember(annotationArg.name!!.asString(), annotationArg.value!!)
                }
                constructorParameter.addAnnotation(annotationSpec.build())
            }
            builder.addSuperclassConstructorParameter(CodeBlock.of("%L", parameter.name!!.asString()))
            constructorBuilder.addParameter(constructorParameter.build())
        }
    }
}
