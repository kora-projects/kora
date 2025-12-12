package ru.tinkoff.kora.s3.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import ru.tinkoff.kora.s3.client.AwsCredentials
import ru.tinkoff.kora.s3.client.model.request.ListObjectsArgs
import ru.tinkoff.kora.s3.client.model.response.ListBucketResult


class S3ListTest : AbstractS3ClientTest() {
    @Test
    @Throws(Exception::class)
    fun testList() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.List
                fun getWithPrefix(creds: AwsCredentials, @Bucket bucket: String, prefix: String): List<String>
            }
            
            """.trimIndent()
        )

        val creds = AwsCredentials.of("test", "test")
        val list = listOf(ListBucketResult.ListBucketItem("test", "test1", "etag", null, null, null, 1L, null, null))

        val listBucketResult = ListBucketResult(null, 1, null, list)

        `when`(
            s3Client.listObjectsV2(
                same(creds), eq("bucket"), assertArg({ o: ListObjectsArgs? -> assertThat<ListObjectsArgs?>(o).hasFieldOrPropertyWithValue("prefix", "test-prefix") })
            )
        )
            .thenReturn(listBucketResult)

        val result = client.invoke<List<String>>("getWithPrefix", creds, "bucket", "test-prefix")
        assertThat(result).containsExactly("test1")

        verify(s3Client).listObjectsV2(
            same(creds), eq("bucket"), assertArg(
                { o: ListObjectsArgs? -> assertThat<ListObjectsArgs?>(o).hasFieldOrPropertyWithValue("prefix", "test-prefix") })
        )
        reset(s3Client)
    }

    @Test
    @Throws(Exception::class)
    fun testListReturnsListBucketResultWithConstPrefix() {
        val client = this.compile(
            """
            @S3.Client
            public interface Client {
                @S3.List("const-")
                fun listConst(creds: AwsCredentials, @Bucket bucket: String): ListBucketResult
            }
            
            """.trimIndent()
        )

        val creds = AwsCredentials.of("a", "b")
        val items = listOf(ListBucketResult.ListBucketItem("b", "k1", "etag", null, null, null, 10L, null, null))
        val res = ListBucketResult(null, 1, null, items)

        `when`(
            s3Client.listObjectsV2(
                same(creds), eq("bucket"), assertArg(
                    { o: ListObjectsArgs? -> assertThat<ListObjectsArgs?>(o).hasFieldOrPropertyWithValue("prefix", "const-") })
            )
        )
            .thenReturn(res)

        val result = client.invoke<ListBucketResult?>("listConst", creds, "bucket")
        assertThat<ListBucketResult?>(result).isSameAs(res)

        verify(s3Client).listObjectsV2(
            same(creds), eq("bucket"), assertArg { o: ListObjectsArgs? -> assertThat<ListObjectsArgs?>(o).hasFieldOrPropertyWithValue("prefix", "const-") }
        )
        reset(s3Client)
    }

    @Test
    fun testIteratorStringLazyPages() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.List
                fun iteratorStrings(creds: AwsCredentials, @Bucket bucket: String, args: ListObjectsArgs): Iterator<String>
            }
            
            """.trimIndent()
        )

        val creds = AwsCredentials.of("x", "y")

        val item1 = ListBucketResult.ListBucketItem("b", "k1", "etag", null, null, null, 1L, null, null)
        val item2 = ListBucketResult.ListBucketItem("b", "k2", "etag", null, null, null, 2L, null, null)

        `when`(
            s3Client.listObjectsV2Iterator(
                same(creds),
                eq("bucket"),
                any()
            )
        )
            .thenReturn(listOf(item1, item2).iterator())

        val args = ListObjectsArgs()
        val it = client.invoke<MutableIterator<String?>?>("iteratorStrings", creds, "bucket", args)

        assertThat(it!!.next()).isEqualTo("k1")
        assertThat(it.next()).isEqualTo("k2")

        verify(s3Client).listObjectsV2Iterator(same<AwsCredentials?>(creds), eq<String?>("bucket"), any<ListObjectsArgs?>())
        reset(s3Client)
    }

    @Test
    fun testIteratorItemsLazyPages() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.List
                fun iteratorItems(creds: AwsCredentials, @Bucket bucket: String, args: ListObjectsArgs): Iterator<ListBucketResult.ListBucketItem>
            }
            """.trimIndent()
        )

        val creds = AwsCredentials.of("x", "y")

        val item1 = ListBucketResult.ListBucketItem("b", "k1", "etag", null, null, null, 1L, null, null)
        val item2 = ListBucketResult.ListBucketItem("b", "k2", "etag", null, null, null, 2L, null, null)

        `when`(
            s3Client.listObjectsV2Iterator(
                same(creds),
                eq("bucket"),
                any()
            )
        )
            .thenReturn(listOf(item1, item2).iterator())

        val args = ListObjectsArgs()
        val it = client.invoke<MutableIterator<ListBucketResult.ListBucketItem?>?>("iteratorItems", creds, "bucket", args)

        assertThat<ListBucketResult.ListBucketItem?>(it!!.next()).isSameAs(item1)
        assertThat<ListBucketResult.ListBucketItem?>(it.next()).isSameAs(item2)

        verify(s3Client).listObjectsV2Iterator(same(creds), eq("bucket"), any())
        reset(s3Client)
    }

    @Test
    @Throws(Exception::class)
    fun testListItemsMappingWithTemplatePrefix() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.List("pre-{prefix}")
                fun items(creds: AwsCredentials, @Bucket bucket: String, prefix: String): List<ListBucketResult.ListBucketItem>
            }
            """.trimIndent()
        )

        val creds = AwsCredentials.of("u", "v")
        val items = listOf(ListBucketResult.ListBucketItem("b", "item-key", "etag", null, null, null, 5L, null, null))
        val res = ListBucketResult(null, 1, null, items)

        `when`(
            s3Client.listObjectsV2(
                same(creds), eq("bucket"), assertArg { o: ListObjectsArgs? -> assertThat<ListObjectsArgs?>(o).hasFieldOrPropertyWithValue("prefix", "pre-x") }
            )
        )
            .thenReturn(res)

        val result = client.invoke<MutableList<ListBucketResult.ListBucketItem?>?>("items", creds, "bucket", "x")
        assertThat<ListBucketResult.ListBucketItem?>(result).hasSize(1)
        assertThat(result!!.get(0)!!.key).isEqualTo("item-key")

        verify(s3Client).listObjectsV2(
            same(creds), eq("bucket"), assertArg(
                { o: ListObjectsArgs? -> assertThat(o).hasFieldOrPropertyWithValue("prefix", "pre-x") })
        )
        reset(s3Client)
    }
}
