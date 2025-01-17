package ru.tinkoff.kora.avro.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import ru.tinkoff.kora.ksp.common.generatedClassName

fun classPackage(classDeclaration: KSClassDeclaration) = classDeclaration.packageName.asString()

fun KSClassDeclaration.readerBinaryName() = this.generatedClassName("AvroBinaryReader")

fun KSClassDeclaration.readerJsonName() = this.generatedClassName("AvroJsonReader")

fun KSClassDeclaration.writerBinaryName() = this.generatedClassName("AvroBinaryWriter")

fun KSClassDeclaration.writerJsonName() = this.generatedClassName("AvroJsonWriter")
