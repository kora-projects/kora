package ru.tinkoff.kora.s3.client.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.s3.client.symbol.processor.gen.BucketsConfigGenerator
import ru.tinkoff.kora.s3.client.symbol.processor.gen.ClientGenerator
import ru.tinkoff.kora.s3.client.symbol.processor.gen.ModuleGenerator
import java.io.IOException


class S3ClientSymbolProcessor(val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val toDefer = mutableListOf<KSAnnotated>()
        for (symbol in resolver.getSymbolsWithAnnotation(S3ClassNames.Annotation.client.canonicalName)) {
            if (symbol.validate()) {
                process(resolver, symbol)
            } else {
                toDefer.add(symbol)
            }
        }
        return toDefer
    }

    private fun process(resolver: Resolver, symbol: KSAnnotated) {
        val s3client = symbol as KSClassDeclaration
        val packageName = s3client.packageName.asString()

        try {
            val bucketsConfig = BucketsConfigGenerator.generate(s3client)
            if (bucketsConfig != null) {
                val configFile = FileSpec.get(packageName, bucketsConfig)
                configFile.writeTo(env.codeGenerator, false)
            }
            val module = ModuleGenerator.generate(s3client)
            val moduleFile = FileSpec.get(packageName, module)
            moduleFile.writeTo(codeGenerator = env.codeGenerator, aggregating = false)

            val client = ClientGenerator.generate(resolver, s3client)
            val implFile = FileSpec.get(packageName, client)
            implFile.writeTo(env.codeGenerator, false)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }

    }
}
