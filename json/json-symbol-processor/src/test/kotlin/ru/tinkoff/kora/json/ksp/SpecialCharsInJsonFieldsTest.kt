package ru.tinkoff.kora.json.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test

class SpecialCharsInJsonFieldsTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testDollarInFieldNames() {
        compile(
            """
            @Json
            data class DtoWithDollarFields(
                @Suppress("PropertyName")
                val `${'$'}ref`: String,
                @Suppress("PropertyName")
                val `ref${'$'}`: String,
                @JsonField("${"\\$\\$"}ref")
                val ref: String,
            )
            """.trimIndent(),
        )

        assertAll(
            {
                val reader = reader("DtoWithDollarFields")

                val readerResult =
                    reader.read(
                        """
                        {
                            "${'$'}ref": "ref 1",
                            "ref${'$'}": "ref 2",
                            "${"$$"}ref": "ref 3"
                        }
                        """.trimIndent(),
                    )

                assertThat(readerResult)
                    .isEqualTo(
                        new(
                            "DtoWithDollarFields",
                            "ref 1",
                            "ref 2",
                            "ref 3",
                        )
                    )
            },
            {
                val writer = writer("DtoWithDollarFields")

                val writerResult =
                    writer.toString(
                        new(
                            "DtoWithDollarFields",
                            "ref with first dollar",
                            "ref with last",
                            "ref with two",
                        ),
                    )

                assertThat(writerResult)
                    .isEqualTo(
                        """
                        {"${'$'}ref":"ref with first dollar","ref${'$'}":"ref with last","${"$$"}ref":"ref with two"}
                        """.trimIndent(),
                    )
            },
        )
    }
}
