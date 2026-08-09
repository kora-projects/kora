package io.koraframework.database.symbol.processor.mongo.extension

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.koraframework.database.symbol.processor.mongo.MongoCodecGenerator
import io.koraframework.database.symbol.processor.mongo.MongoTypes
import io.koraframework.kora.app.ksp.extension.ExtensionResult
import io.koraframework.kora.app.ksp.extension.KoraExtension
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent

// Codec<T> for types annotated with @EntityMongo
class MongoTypesExtension : KoraExtension {

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        if (tag != null) {
            return null
        }
        if (type.declaration.qualifiedName?.asString() != MongoTypes.codec.canonicalName) {
            return null
        }

        val entityType = type.arguments.singleOrNull()?.type?.resolve() ?: return null
        if (entityType.isMarkedNullable) {
            return null
        }
        val declaration = entityType.declaration as? KSClassDeclaration ?: return null
        if (!declaration.isAnnotationPresent(MongoTypes.mongoEntity)) {
            return null
        }
        return generatedByProcessorWithName(resolver, declaration, MongoCodecGenerator.codecName(declaration))
    }
}
