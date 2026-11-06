package io.koraframework.aop.ksp

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.common.annotation.Component
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class AopAnnotationProcessorTest : AbstractSymbolProcessorTest() {
    @Test
    fun testAopBeforeAndAfterCalled() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("testAopBeforeAndAfterCalled")
                open fun test() {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val aopTarget = loadClass("AopTarget")
        val aopProxy = loadClass("\$AopTarget__AopProxy")

        assertThat(aopTarget).isNotNull()
        assertThat(aopProxy).isNotNull().isAssignableTo(aopTarget)


        val listener = mock<TestMethodCallListener>()

        val testObject = TestObject(aopTarget.kotlin, new("\$AopTarget__AopProxy", listener))

        testObject.invoke<Unit>("test")

        val order = inOrder(listener)
        order.verify(listener).before("testAopBeforeAndAfterCalled")
        order.verify(listener).after(eq("testAopBeforeAndAfterCalled"), eq(Unit))
        order.verify(listener, never()).thrown(any(), any())
        order.verifyNoMoreInteractions()
    }

    @Test
    fun testNotAnnotatedMethodsNotProxied() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                open fun test1() {}
                @io.koraframework.aop.ksp.TestAnnotation1("testNotAnnotatedMethodsNotProxied")
                open fun test2() {}
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val aopProxy = loadClass("\$AopTarget__AopProxy")

        val methods = aopProxy.declaredMethods.filter { m: Method -> Modifier.isPublic(m.modifiers) }

        assertThat(methods).hasSize(1)
        assertThat(methods[0].name).isEqualTo("test2")
    }

    @Test
    fun testNoopAspectAfterAppliedAspectDoesNotCreateOverride() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("test")
                open fun test1(id: Long): Long = id

                @io.koraframework.aop.ksp.TestNoopAnnotation
                open fun test2(id: Long): Long = id
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val aopProxy = loadClass("\$AopTarget__AopProxy")

        val methods = aopProxy.declaredMethods.filter { m: Method -> Modifier.isPublic(m.modifiers) }

        assertThat(methods).hasSize(1)
        assertThat(methods[0].name).isEqualTo("test1")
    }

    @Test
    fun testClassLevelAspectsApplied() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            @io.koraframework.aop.ksp.TestAnnotation1("testClassLevelAspectsApplied")
            open class AopTarget {
                open fun test1() {}
                open fun test2() {}
            }
            """.trimIndent())
        compileResult.assertSuccess()
        val aopProxy = loadClass("\$AopTarget__AopProxy")

        val methods = aopProxy.declaredMethods.filter { m: Method -> Modifier.isPublic(m.modifiers) }
        assertThat(methods).hasSize(2)

        val listener = mock<TestMethodCallListener>()

        val testObject = TestObject(loadClass("AopTarget").kotlin, new("\$AopTarget__AopProxy", listener))
        testObject.invoke<Unit>("test1")
        testObject.invoke<Unit>("test2")

        val order = inOrder(listener)
        order.verify(listener).before("testClassLevelAspectsApplied")
        order.verify(listener).after(eq("testClassLevelAspectsApplied"), eq(Unit))
        order.verify(listener).before("testClassLevelAspectsApplied")
        order.verify(listener).after(eq("testClassLevelAspectsApplied"), eq(Unit))
        order.verify(listener, never()).thrown(any(), any())
        order.verifyNoMoreInteractions()
    }

    @Test
    fun testParameterLevelAspectApplied() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                open fun test(@io.koraframework.aop.ksp.TestAnnotation1("testParameterLevelAspectApplied") param: String) {}
            }
            
            """.trimIndent())
        compileResult.assertSuccess()

        val listener = mock<TestMethodCallListener>()

        val testObject = TestObject(loadClass("AopTarget").kotlin, new("\$AopTarget__AopProxy", listener))
        testObject.invoke<Unit>("test", "param")

        val order = inOrder(listener)
        order.verify(listener).before("testParameterLevelAspectApplied")
        order.verify(listener).after(eq("testParameterLevelAspectApplied"), eq(Unit))
        order.verify(listener, never()).thrown(any(), any())
        order.verifyNoMoreInteractions()
    }

    @Test
    fun testMethodLevelAnnotationAppliedInCorrectOrder() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("TestAnnotation1")
                @io.koraframework.aop.ksp.TestAnnotation2("TestAnnotation2")
                open fun test() {}
            }
            
            """.trimIndent())
        compileResult.assertSuccess()

        val listener = mock<TestMethodCallListener>()

        val testObject = TestObject(loadClass("AopTarget").kotlin, new("\$AopTarget__AopProxy", listener))
        testObject.invoke<Unit>("test")

        val order = inOrder(listener)
        order.verify(listener).before("TestAnnotation1")
        order.verify(listener).before("TestAnnotation2")
        order.verify(listener).after(eq("TestAnnotation2"), eq(Unit))
        order.verify(listener).after(eq("TestAnnotation1"), eq(Unit))
        order.verify(listener, never()).thrown(any(), any())
        order.verifyNoMoreInteractions()
    }


    @Test
    fun testClassAndMethodLevelAspectAppliedInCorrectOrder() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            @io.koraframework.aop.ksp.TestAnnotation2("TestAnnotation2")
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("TestAnnotation1")
                @io.koraframework.aop.ksp.TestAnnotation2("TestAnnotation2")
                open fun classLevelOverriden() {}
            
                @io.koraframework.aop.ksp.TestAnnotation1("TestAnnotation1")
                open fun classLevelFirst() {}
            }
            
            """.trimIndent())
        compileResult.assertSuccess()

        val listener = mock<TestMethodCallListener>()

        val testObject = TestObject(loadClass("AopTarget").kotlin, new("\$AopTarget__AopProxy", listener))
        testObject.invoke<Unit>("classLevelOverriden")

        var order = inOrder(listener)
        order.verify(listener).before("TestAnnotation1")
        order.verify(listener).before("TestAnnotation2")
        order.verify(listener).after(eq("TestAnnotation2"), eq(Unit))
        order.verify(listener).after(eq("TestAnnotation1"), eq(Unit))
        order.verify(listener, never()).thrown(any(), any())
        order.verifyNoMoreInteractions()
        reset(listener)

        testObject.invoke<Unit>("classLevelFirst")

        order = inOrder(listener)
        order.verify(listener).before("TestAnnotation2")
        order.verify(listener).before("TestAnnotation1")
        order.verify(listener).after(eq("TestAnnotation1"), eq(Unit))
        order.verify(listener).after(eq("TestAnnotation2"), eq(Unit))
        order.verify(listener, never()).thrown(any(), any())
        order.verifyNoMoreInteractions()
    }

    @Test
    fun testTaggedConstructorParamsPropagetedToProxy() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget(arg1: String?, @Tag(String::class) arg2: Int?) {
                @io.koraframework.aop.ksp.TestAnnotation1("test")
                open fun test() {}
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val aopTarget = loadClass("AopTarget")
        val aopProxy = loadClass("\$AopTarget__AopProxy")

        val targetConstructor = aopTarget.constructors[0]
        val aopProxyConstructor = aopProxy.constructors[0]
        for (i in 0 until targetConstructor.parameterCount) {
            val targetParam = targetConstructor.parameters[i]
            val proxyParam = aopProxyConstructor.parameters[i]
            assertThat(targetParam.annotations).containsExactly(*proxyParam.annotations)
        }
    }

    @Test
    fun componentAnnotationPropagadedOnProxy() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            @Component
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("test")
                open fun test() {}
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val aopTarget = loadClass("AopTarget")
        val aopProxy = loadClass("\$AopTarget__AopProxy")

        assertThat(aopProxy.annotations).anyMatch { it.annotationClass == Component::class }
    }

    @Test
    fun testGenericFunctionWithSeveralAspects() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("TestAnnotation1")
                @io.koraframework.aop.ksp.TestAnnotation2("TestAnnotation2")
                open fun <T> test(value: T): T = value
            }
            """.trimIndent())
        compileResult.assertSuccess()

        val listener = mock<TestMethodCallListener>()
        val testObject = TestObject(loadClass("AopTarget").kotlin, new("\$AopTarget__AopProxy", listener))
        assertThat(testObject.invoke<String>("test", "value")).isEqualTo("value")

        val order = inOrder(listener)
        order.verify(listener).before("TestAnnotation1")
        order.verify(listener).before("TestAnnotation2")
        order.verify(listener).after(eq("TestAnnotation2"), eq("value"))
        order.verify(listener).after(eq("TestAnnotation1"), eq("value"))
        order.verifyNoMoreInteractions()
    }

    @Test
    fun testVarargParametersProxied() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("test")
                open fun test(prefix: String, vararg values: String): String = prefix + values.joinToString()

                @io.koraframework.aop.ksp.TestAnnotation1("sum")
                open fun sum(vararg values: Int): Int = values.sum()
            }
            """.trimIndent())
        compileResult.assertSuccess()

        val listener = mock<TestMethodCallListener>()
        val testObject = TestObject(loadClass("AopTarget").kotlin, new("\$AopTarget__AopProxy", listener))
        assertThat(testObject.invoke<String>("test", ">", arrayOf("a", "b"))).isEqualTo(">a, b")
        assertThat(testObject.invoke<Int>("sum", intArrayOf(1, 2))).isEqualTo(3)

        verify(listener).after("test", ">a, b")
        verify(listener).after("sum", 3)
    }

    @Test
    fun testInternalFunctionProxied() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("testInternalFunctionProxied")
                internal open fun test() {}
            }
            """.trimIndent())
        compileResult.assertSuccess()
        val aopTarget = loadClass("AopTarget")
        val aopProxy = loadClass("\$AopTarget__AopProxy")
        val targetMethod = aopTarget.declaredMethods.single { it.name.startsWith("test") }
        val proxyMethod = aopProxy.declaredMethods.single { it.name == targetMethod.name }

        val listener = mock<TestMethodCallListener>()
        proxyMethod.invoke(new("\$AopTarget__AopProxy", listener))

        val order = inOrder(listener)
        order.verify(listener).before("testInternalFunctionProxied")
        order.verify(listener).after(eq("testInternalFunctionProxied"), eq(Unit))
        order.verifyNoMoreInteractions()
    }

    @Test
    fun testExtensionFunctionAspectReportsError() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("test")
                open fun String.test(): String = this
            }
            """.trimIndent())

        assertThat(compileResult.assertFailure().messages.joinToString("\n"))
            .contains("extension function")
    }

    @Test
    fun testConstructorParamNamedLikeAspectFieldPropagatedToProxy() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget(val testMethodCallListener1: String) {
                @io.koraframework.aop.ksp.TestAnnotation1("test")
                open fun test(): String = testMethodCallListener1
            }
            """.trimIndent())
        compileResult.assertSuccess()

        val listener = mock<TestMethodCallListener>()
        val testObject = TestObject(loadClass("AopTarget").kotlin, new("\$AopTarget__AopProxy", "value", listener))
        assertThat(testObject.invoke<String>("test")).isEqualTo("value")

        verify(listener).after("test", "value")
    }

    @Test
    fun testGenericClassProxied() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            open class AopTarget<T> {
                @io.koraframework.aop.ksp.TestAnnotation1("test")
                open fun test(value: T): T = value
            }
            """.trimIndent())
        compileResult.assertSuccess()
        assertThat(loadClass("\$AopTarget__AopProxy").typeParameters).hasSize(1)

        val listener = mock<TestMethodCallListener>()
        val testObject = TestObject(loadClass("AopTarget").kotlin, new("\$AopTarget__AopProxy", listener))
        assertThat(testObject.invoke<String>("test", "value")).isEqualTo("value")

        verify(listener).after("test", "value")
    }

    @Test
    fun interfacesAreNotBeingProcessedByAopProcessor() {
        compile0(listOf(AopSymbolProcessorProvider()), """
            interface AopTarget {
                @io.koraframework.aop.ksp.TestAnnotation1("test")
                fun test()
            }
            
            """.trimIndent())
        compileResult.assertSuccess()

        val aopTarget = loadClass("AopTarget")

        Assertions.assertThatThrownBy { loadClass("\$AopTarget__AopProxy") }
            .isInstanceOf(ClassNotFoundException::class.java)
    }

}
