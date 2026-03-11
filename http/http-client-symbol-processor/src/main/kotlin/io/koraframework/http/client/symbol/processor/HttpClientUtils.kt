package io.koraframework.http.client.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.koraframework.ksp.common.getOuterClassesAsPrefix

fun KSClassDeclaration.clientName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_ClientImpl"

fun KSClassDeclaration.configName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_Config"

fun KSClassDeclaration.moduleName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_Module"

