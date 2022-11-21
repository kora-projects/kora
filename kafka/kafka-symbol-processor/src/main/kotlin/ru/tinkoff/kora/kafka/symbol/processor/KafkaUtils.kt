package ru.tinkoff.kora.kafka.symbol.processor

fun prepareTagName(controllerName: String, methodName: String): String {
    return controllerName.replaceFirstChar { it.uppercaseChar() } + methodName.replaceFirstChar { it.uppercaseChar() } + "Tag"
}

fun prepareMethodName(controllerName: String, methodName: String): String {
    return controllerName.replaceFirstChar { it.lowercaseChar() } + methodName.replaceFirstChar { it.uppercaseChar() }
}
