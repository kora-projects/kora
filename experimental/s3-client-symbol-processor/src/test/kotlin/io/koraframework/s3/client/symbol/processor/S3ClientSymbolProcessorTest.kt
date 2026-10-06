package io.koraframework.s3.client.symbol.processor

import io.koraframework.common.annotation.Tag
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.GraphUtil.toGraphDraw
import io.koraframework.s3.client.kora.symbol.processor.AbstractS3ClientTest
import io.koraframework.s3.client.kora.symbol.processor.S3ClientSymbolProvider
import io.koraframework.ksp.common.exception.ProcessingErrorException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class S3ClientSymbolProcessorTest : AbstractS3ClientTest() {

    companion object {
        @JvmField
        val USED_HTTP_CLIENT_PROVIDERS: MutableList<String> = CopyOnWriteArrayList()
    }

    @Test
    fun testFactoryTag() {
        this.compile(
            """
            @S3.Client(factoryTag = Client.CustomS3FactoryTag::class)
            interface Client {
                class CustomS3FactoryTag

                @S3.List
                fun list(creds: S3Credentials, @Bucket bucket: String, prefix: String): List<String>
            }
            """.trimIndent()
        )

        val clientImpl = loadClass("\$Client_S3Module")
            .methods
            .first { it.name == "clientImpl" }
        val clientFactoryTag = clientImpl.parameters[0].getAnnotation(Tag::class.java)

        assertThat(clientFactoryTag.value.java).isEqualTo(loadClass("Client\$CustomS3FactoryTag"))
    }

    @Test
    fun testSuspendMethodIsRejected() {
        assertThatThrownBy {
            compile0(
                listOf(S3ClientSymbolProvider()),
                """
                @S3.Client
                interface Client {
                    @S3.List
                    suspend fun list(@Bucket bucket: String): List<String>
                }
                """.trimIndent()
            )
        }.isInstanceOfSatisfying(ProcessingErrorException::class.java) {
            assertThat(it.message)
                .contains("Suspend methods are not supported by the S3 client generator")
                .contains("--enable-preview")
                .contains("StructuredTaskScope.open")
                .contains("Remove suspend from the method")
        }
    }

    @Test
    fun testTaggedFactoryModuleUsesTaggedHttpClientProvider() {
        compile0(
            listOf(KoraAppProcessorProvider()),
            """
            import io.koraframework.http.client.common.HttpClient
            import io.koraframework.s3.client.symbol.processor.S3ClientSymbolProcessorTest

            @KoraApp
            interface ExampleApplication : KoraS3ClientModule {
                class Second

                @Tag(Second::class)
                @FactoryModule
                fun secondS3(): S3FactoryModule = S3FactoryModule("s3second")

                fun defaultHttpClient(): HttpClient = HttpClient { throw IllegalStateException("default") }

                @Tag(Second::class)
                fun secondHttpClientProvider(): S3HttpClientProvider = S3HttpClientProvider {
                    S3ClientSymbolProcessorTest.USED_HTTP_CLIENT_PROVIDERS.add("second")
                    HttpClient { throw IllegalStateException("second") }
                }

                @Root
                fun root(@Tag(Second::class) second: S3ClientFactory): Any = second
            }
            """.trimIndent()
        ).assertSuccess()

        val draw = loadClass("ExampleApplicationGraph").toGraphDraw()
        USED_HTTP_CLIENT_PROVIDERS.clear()
        draw.init()
        assertThat(USED_HTTP_CLIENT_PROVIDERS).containsExactly("second")
    }
}
