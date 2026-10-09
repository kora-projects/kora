package io.koraframework.http.client.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import io.koraframework.ksp.common.getOuterClassesAsPrefix

/**
 * Client implementation and config are generated as nested types of the client module,
 * e.g. `$MyClient_Module.Impl` and `$MyClient_Module.Config`
 */
const val CLIENT_NAME = "Impl"
const val CONFIG_NAME = "Config"

/**
 * @return name of the top level client class that was generated before client was moved into the module
 */
fun KSClassDeclaration.clientName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_ClientImpl"

fun KSClassDeclaration.moduleName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_Module"

fun KSClassDeclaration.moduleClassName() = ClassName(packageName.asString(), moduleName())

fun KSClassDeclaration.clientClassName() = moduleClassName().nestedClass(CLIENT_NAME)

fun KSClassDeclaration.configClassName() = moduleClassName().nestedClass(CONFIG_NAME)
