package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import org.openapitools.codegen.model.OperationsMap
import ru.tinkoff.kora.openapi.generator.DelegateMethodBodyMode

class ServerApiModuleGenerator : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val b = TypeSpec.interfaceBuilder(ctx["classname"].toString() + "Module")
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())

        if (params.delegateMethodBodyMode != DelegateMethodBodyMode.NONE) {
            val delegateClass = ClassName(apiPackage, ctx["classname"].toString() + "Delegate");
            val o = TypeSpec.anonymousClassBuilder()
                .addAnnotation(generated())
                .addSuperinterface(delegateClass)
                .build()
            b.addFunction(FunSpec.builder("default" + ctx.get("classname") + "Delegate")
                .addStatement("return %L", o)
                .returns(delegateClass)
                .build());
        }

        return FileSpec.get(apiPackage, b.build())

    }
}
