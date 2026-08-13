package io.koraframework.database.symbol.processor

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.database.symbol.processor.cassandra.CassandraRepositoryGenerator
import io.koraframework.database.symbol.processor.jdbc.JdbcRepositoryGenerator
import io.koraframework.database.symbol.processor.DbUtils.findQueryMethods
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.CommonAopUtils.extendsKeepAopAll
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.FunctionUtils.isSuspend
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.getOuterClassesAsPrefix
import org.slf4j.LoggerFactory

class RepositoryBuilder(
    val resolver: Resolver,
    private val kspLogger: KSPLogger
) {
    private val availableGenerators = listOf(
        JdbcRepositoryGenerator(resolver),
        CassandraRepositoryGenerator(resolver),
    )
    private val log = LoggerFactory.getLogger(RepositoryBuilder::class.java)


    fun build(repositoryDeclaration: KSClassDeclaration): TypeSpec? {
        log.debug("Generating Repository for {}", repositoryDeclaration.simpleName.asString())
        repositoryDeclaration.findQueryMethods().firstOrNull { it.isSuspend() }?.let { method ->
            throw ProcessingErrorException(
                """
                Repository method is invalid:
                  ${method.simpleName.asString()}

                Problem:
                  Suspend methods are not supported by the repository generator.

                Hint:
                  Generated repositories support regular blocking signatures and supported async wrapper return types, not Kotlin suspend functions.
                  For structured concurrency, enable Java preview features with --enable-preview and use StructuredTaskScope, for example:

                    fun getProfile(id: Long): Profile =
                        StructuredTaskScope.open(
                            StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow<Any>(),
                        ).use { scope ->
                            val user = scope.fork(Callable { userRepository.find(id) })
                            val orders = scope.fork(Callable { orderRepository.findByUser(id) })

                            scope.join()

                            Profile(user.get(), orders.get())
                        }

                Fix:
                  Remove suspend from the method or expose an async return type supported by the repository backend.
                """.trimIndent(),
                method
            )
        }
        val name = repositoryDeclaration.getOuterClassesAsPrefix() + repositoryDeclaration.simpleName.asString() + "_Impl"
        val builder = repositoryDeclaration.extendsKeepAopAll(name, resolver)
            .generated(RepositoryBuilder::class)
            .addOriginatingKSFile(repositoryDeclaration)

        repositoryDeclaration.findAnnotation(CommonClassNames.root)
            ?.let { builder.addAnnotation(it.toAnnotationSpec()) }

        repositoryDeclaration.findAnnotation(CommonClassNames.component)
            ?.let { builder.addAnnotation(it.toAnnotationSpec()) }

        repositoryDeclaration.findAnnotation(CommonClassNames.tag)
            ?.let { builder.addAnnotation(it.toAnnotationSpec()) }

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
        throw ProcessingErrorException(
            """
            Repository type is invalid:
              ${repositoryDeclaration.qualifiedName?.asString()}

            Problem:
              @Repository type doesn't extend any supported repository interface.

            Hint:
              Kora chooses the repository generator by a known base interface, for example JDBC or Cassandra repository contract.

            Fix:
              Extend one of the supported repository interfaces, or remove @Repository from this type.
            """.trimIndent(),
            repositoryDeclaration
        )
    }

    private fun enrichConstructorFromParentClass(builder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder, repositoryDeclaration: KSClassDeclaration) {
        val constructors = repositoryDeclaration.getConstructors().filter { !it.isProtected() }.toList()
        if (constructors.isEmpty()) {
            return
        }
        if (constructors.size > 1) {
            throw ProcessingErrorException(
                """
                Repository class has ambiguous constructors:
                  ${repositoryDeclaration.qualifiedName?.asString()}

                Problem:
                  Abstract repository class has more than one non-private constructor.

                Hint:
                  Generated repository implementation must call exactly one parent constructor.

                Fix:
                  Keep a single non-private constructor, or make extra constructors private.
                """.trimIndent(),
                repositoryDeclaration
            )
        }
        val constructor = constructors[0]
        val parameters = constructor.parameters
        for (i in parameters.indices) {
            val parameter = parameters[i]
            val constructorParameter = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.toTypeName())
            for (annotation in parameter.annotations) {
                val annotationSpec = AnnotationSpec.builder(annotation.annotationType.resolve().declaration.let { it as KSClassDeclaration }.toClassName())
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
