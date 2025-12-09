package ru.tinkoff.kora.aws.s3.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.intellij.lang.annotations.Language
import org.mockito.Mockito
import ru.tinkoff.kora.aws.s3.S3Client
import ru.tinkoff.kora.aws.s3.S3ClientConfigWithCredentials
import ru.tinkoff.kora.aws.s3.S3ClientFactory
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest


abstract class AbstractS3ClientTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import java.nio.ByteBuffer
            import java.io.InputStream
            import ru.tinkoff.kora.aws.s3.annotation.*
            import ru.tinkoff.kora.aws.s3.annotation.S3.*
            import ru.tinkoff.kora.aws.s3.model.request.*
            import ru.tinkoff.kora.aws.s3.model.response.*
            import ru.tinkoff.kora.aws.s3.*
            import ru.tinkoff.kora.aws.s3.S3Client.*
            """.trimIndent()
    }

    protected var s3Client = Mockito.mock(S3Client::class.java)
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
