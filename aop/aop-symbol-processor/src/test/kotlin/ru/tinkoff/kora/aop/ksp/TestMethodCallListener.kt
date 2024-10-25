package ru.tinkoff.kora.aop.ksp

interface TestMethodCallListener {
    fun before(annotationValue: String)

    fun after(annotationValue: String, result: Any?)

    fun thrown(annotationValue: String, throwable: Throwable)
}
