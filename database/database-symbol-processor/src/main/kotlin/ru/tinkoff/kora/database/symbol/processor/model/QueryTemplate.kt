package ru.tinkoff.kora.database.symbol.processor.model

import com.google.devtools.ksp.KspExperimental

@KspExperimental
object QueryTemplate {
    /*
    fun processTemplate(resolver: Resolver, source: String, method: KSFunctionDeclaration, methodType: KSFunction, queryResult: QueryResult): String {
        val templateParams = TemplateModel.detectTemplateParams(source)
        if (templateParams.isEmpty()) {
            return source
        }
        val returnTypeModel = detectReturnTypeModel(resolver, queryResult)
        val returnTypeTemplateParams = returnTypeModel?.getTemplateParams(null)
        val templates = templateParams
            .mapNotNull { it.paramName }
            .distinct()
            .associateWith { paramName ->
                val entityModel = method.parameters.zip(methodType.parameterTypes)
                    .firstOrNull {
                        it.first.name!!.asString() == paramName
                            && it.second!!.declaration is KSClassDeclaration
                            && (it.second!!.declaration as KSClassDeclaration).classKind == ClassKind.CLASS
                    }
                    ?.let { TemplateModel.parseEntityModel(resolver, it.second!!.declaration as KSClassDeclaration) }
                    ?: throw RuntimeException(String.format("Unknown parameter '%s' for query `%s`", paramName, source))
                entityModel
            }

        val templatesParams = hashMapOf<String, Map<String, String>>()
        templates.forEach { (param: String?, template: TemplateModel) ->
            templatesParams[param] = template.getTemplateParams(param)
        }
        var sql = source
        for (templateParam in templateParams) {
            val paramName = templateParam.paramName
            val params: Map<String, String>? = if (paramName == null) {
                returnTypeTemplateParams
            } else {
                templatesParams[paramName]
            }
            val value = params!![templateParam.template] ?: throw RuntimeException(String.format("Unknown template `%s` for query %s", templateParam.rawTemplate, source))
            sql = sql.replace(templateParam.rawTemplate, value)
        }
        return sql
    }

    private fun detectReturnTypeModel(resolver: Resolver, result: QueryResult): TemplateModel? {
        var actualResult = result
        return null
    }

     */
}
