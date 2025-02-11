package ru.tinkoff.kora.s3.client.symbol.processor

import org.intellij.lang.annotations.Language
import org.mockito.Mockito
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.s3.client.S3Client
import ru.tinkoff.kora.s3.client.S3ClientFactory

abstract class AbstractS3Test : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import java.nio.ByteBuffer;
            import java.io.InputStream;
            import ru.tinkoff.kora.s3.client.annotation.*;
            import ru.tinkoff.kora.s3.client.annotation.S3.*;
            import ru.tinkoff.kora.s3.client.model.*;
            import ru.tinkoff.kora.s3.client.*;
            import ru.tinkoff.kora.s3.client.S3Client.*;
            """.trimIndent()
    }

    protected var s3Client: S3Client = Mockito.mock(S3Client::class.java)

    protected fun compile(@Language("kotlin") source: String, vararg addArgs: Any?): TestObject {
        val result = this.compile0(listOf(S3ClientSymbolProcessorProvider(), AopSymbolProcessorProvider()), source)
        result.assertSuccess()
        val args = ArrayList<Any?>(1 + addArgs.size)
        args.add(S3ClientFactory { s3Client })
        args.addAll(addArgs)
        return newObject("\$Client_Impl", *args.toArray())
    }
}
