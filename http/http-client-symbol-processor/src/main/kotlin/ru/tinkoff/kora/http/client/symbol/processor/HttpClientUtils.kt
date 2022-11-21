package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

fun KSClassDeclaration.clientName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_ClientImpl"

fun KSClassDeclaration.configName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_Config"

fun KSClassDeclaration.moduleName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_Module"
