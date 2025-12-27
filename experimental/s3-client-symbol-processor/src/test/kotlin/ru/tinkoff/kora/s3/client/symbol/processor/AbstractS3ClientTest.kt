package ru.tinkoff.kora.s3.client.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.intellij.lang.annotations.Language
import org.mockito.Answers
import org.mockito.Mockito
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.s3.client.S3Client
import ru.tinkoff.kora.s3.client.S3ClientConfigWithCredentials
import ru.tinkoff.kora.s3.client.S3ClientFactory


abstract class AbstractS3ClientTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import java.nio.ByteBuffer
            import java.io.InputStream
            import ru.tinkoff.kora.s3.client.annotation.*
            import ru.tinkoff.kora.s3.client.annotation.S3.*
            import ru.tinkoff.kora.s3.client.model.request.*
            import ru.tinkoff.kora.s3.client.model.response.*
            import ru.tinkoff.kora.s3.client.*
            import ru.tinkoff.kora.s3.client.S3Client.*
            """.trimIndent()
    }

    protected var s3Client = Mockito.mock(S3Client::class.java, Answers.CALLS_REAL_METHODS)
    protected var config = Mockito.mock(S3ClientConfigWithCredentials::class.java)

    protected fun compile(@Language("kotlin") source: String, vararg addArgs: Any?): TestObject {
        val result = this.compile0(listOf<SymbolProcessorProvider>(S3ClientSymbolProvider()), source)
        result.assertSuccess()
        val clientFactory = S3ClientFactory { config -> s3Client }
        val args = ArrayList<Any?>(2 + addArgs.size)
        args.add(clientFactory)
        args.add(config)
        args.addAll(addArgs.map { if (it is GeneratedObject<*>) it() else it })
        return newObject("\$Client_ClientImpl", *args.toTypedArray())
    }
}
