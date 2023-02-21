package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

fun KSClassDeclaration.clientName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_ClientImpl"

fun KSClassDeclaration.configName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_Config"

fun KSClassDeclaration.moduleName() = getOuterClassesAsPrefix() + simpleName.getShortName() + "_Module"

val httpClientAnnotation = ClassName("ru.tinkoff.kora.http.client.common.annotation", "HttpClient")
val httpClient = ClassName("ru.tinkoff.kora.http.client.common", "HttpClient")
val httpClientRequestMapper = ClassName("ru.tinkoff.kora.http.client.common.request", "HttpClientRequestMapper")
val httpClientResponseMapper = ClassName("ru.tinkoff.kora.http.client.common.response", "HttpClientResponseMapper")
