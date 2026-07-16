package io.koraframework.avro.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.koraframework.ksp.common.generatedClassName

fun classPackage(classDeclaration: KSClassDeclaration) = classDeclaration.packageName.asString()

fun KSClassDeclaration.readerName() = this.generatedClassName("AvroReader")

fun KSClassDeclaration.writerName() = this.generatedClassName("AvroWriter")
