package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames.aopAnnotation

object CommonAopUtils {
    fun KSClassDeclaration.extendsKeepAop(newName: String): TypeSpec.Builder {
        val type = this
        val b: TypeSpec.Builder = TypeSpec.classBuilder(newName)
            .addOriginatingKSFile(type.containingFile!!)
        if (type.classKind == ClassKind.INTERFACE) {
            b.addSuperinterface(type.toClassName())
        } else {
            b.superclass(type.toClassName())
        }

        if(hasAopAnnotations(type)) {
            b.addModifiers(KModifier.OPEN)
        } else {
            b.addModifiers(KModifier.FINAL)
        }

        for (annotationMirror in type.annotations) {
            if (isAopAnnotation(annotationMirror)) {
                b.addAnnotation(annotationMirror.toAnnotationSpec())
            }
        }
        return b
    }

    fun KSFunctionDeclaration.overridingKeepAop(resolver: Resolver): FunSpec.Builder {
        val funDeclaration = this
        val funBuilder = FunSpec.builder(funDeclaration.simpleName.asString())
        if (funDeclaration.modifiers.contains(Modifier.SUSPEND)) {
            funBuilder.addModifiers(KModifier.SUSPEND)
        }
        if (funDeclaration.modifiers.contains(Modifier.PROTECTED)) {
            funBuilder.addModifiers(KModifier.PROTECTED)
        }
        if (funDeclaration.modifiers.contains(Modifier.PUBLIC)) {
            funBuilder.addModifiers(KModifier.PUBLIC)
        }

        for (typeParameter in funDeclaration.typeParameters) {
            funBuilder.addTypeVariable(typeParameter.toTypeVariableName())
        }
        funBuilder.addModifiers(KModifier.OVERRIDE)
        for (annotation in funDeclaration.annotations) {
            if (isAopAnnotation(annotation)) {
                funBuilder.addAnnotation(annotation.toAnnotationSpec())
            }
        }
        val returnType = funDeclaration.returnType!!.resolve()
        if (returnType != resolver.builtIns.unitType) {
            funBuilder.returns(returnType.toTypeName())
        }
        for (parameter in funDeclaration.parameters) {
            val parameterType = parameter.type
            val name = parameter.name!!.asString()
            val pb = ParameterSpec.builder(name, parameterType.toTypeName())
            if (parameter.isVararg) {
                pb.addModifiers(KModifier.VARARG)
            }
            for (annotation in parameter.annotations) {
                if (isAopAnnotation(annotation)) {
                    pb.addAnnotation(annotation.toAnnotationSpec())
                }
            }
            funBuilder.addParameter(pb.build())
        }

        return funBuilder
    }

    fun hasAopAnnotations(ksAnnotated: KSAnnotated): Boolean {
        if (hasAopAnnotation(ksAnnotated)) {
            return true
        }
        val methods = findMethods(ksAnnotated) { f ->
            f.isPublic() || f.isProtected()
        }
        for (method in methods) {
            if (hasAopAnnotation(method)) {
                return true
            }
            for (parameter in method.parameters) {
                if (hasAopAnnotation(parameter)) {
                    return true
                }
            }
        }
        return false
    }

    fun hasAopAnnotation(e: KSAnnotated): Boolean {
        return e.annotations.any { isAopAnnotation(it) }
    }

    fun isAopAnnotation(annotation: KSAnnotation): Boolean {
        return annotation.annotationType.resolve().declaration.isAnnotationPresent(aopAnnotation)
    }

}
