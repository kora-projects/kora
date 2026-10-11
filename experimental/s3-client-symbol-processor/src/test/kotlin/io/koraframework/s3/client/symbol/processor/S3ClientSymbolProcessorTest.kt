package io.koraframework.s3.client.symbol.processor

import io.koraframework.common.annotation.Tag
import io.koraframework.s3.client.kora.symbol.processor.AbstractS3ClientTest
import io.koraframework.s3.client.kora.symbol.processor.S3ClientSymbolProvider
import io.koraframework.ksp.common.exception.ProcessingErrorException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class S3ClientSymbolProcessorTest : AbstractS3ClientTest() {

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
}
