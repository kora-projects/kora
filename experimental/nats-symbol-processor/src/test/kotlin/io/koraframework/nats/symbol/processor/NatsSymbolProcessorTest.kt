package io.koraframework.nats.symbol.processor

import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NatsSymbolProcessorTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String = super.commonImports() + """
        import io.koraframework.nats.common.annotation.*
        import io.koraframework.nats.common.producer.*
        import io.koraframework.nats.common.consumer.*
        import io.koraframework.nats.common.consumer.telemetry.NatsConsumerPollObservation
        import io.koraframework.nats.common.exceptions.NatsSerializationException
        import io.nats.client.*
        import io.nats.client.api.PublishAck
        import io.nats.client.impl.Headers
        import java.util.concurrent.CompletableFuture
        import java.util.concurrent.CompletionStage
    """.trimIndent()

    @Test
    fun publisherSignatures() {
        compile0(
            listOf(NatsSymbolProcessorProvider()), """
            @NatsPublisher("publisher")
            interface Events {
                @NatsPublisher.Subject("events") fun send(value: String, headers: Headers?)
                @NatsPublisher.Subject("eventsAck") fun sendAck(value: Long, options: PublishOptions): PublishAck
                fun sendAsync(record: NatsProducerMessage<String>): CompletableFuture<PublishAck>
                fun sendCompletion(message: Message): CompletionStage<Void>
                fun sendCallback(message: Message, callback: NatsPublishCallback)
                @NatsPublisher.Subject("requests") @NatsPublisher.Request
                fun request(value: String, timeout: java.time.Duration): String
                @NatsPublisher.Request fun requestRaw(message: Message): CompletableFuture<Message>
                fun helper(): String = "helper"
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("\$Events_Impl").interfaces).contains(loadClass("Events"))
        assertThat(loadClass("Events_PublisherModule").methods).hasSize(8)
    }

    @Test
    fun listenerSignaturesAndReply() {
        compile0(
            listOf(NatsSymbolProcessorProvider()), """
            class Listeners {
                @NatsListener("body") fun body(value: String?, error: NatsSerializationException?, message: Message, headers: Headers, connection: Connection, poll: NatsConsumerPollObservation) {}
                @NatsListener("record") fun record(record: NatsMessage<String>, error: Exception?) {}
                @NatsListener(value = "batch", tag = Long::class) fun batch(records: NatsMessages<String>, connection: Connection, poll: NatsConsumerPollObservation) {}
                @NatsListener("raw") fun raw(message: Message) {}
                @NatsListener("reply") @Tag(Long::class) fun reply(@Tag(Int::class) value: String): String = value
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("Listeners_NatsListenerModule").methods).hasSize(20)
    }

    @Test
    fun generatedListenerReceivesDecodeErrorAndPollObservation() {
        compile0(
            listOf(NatsSymbolProcessorProvider()), """
            class Listeners {
                var value: String? = "initial"
                var error: NatsSerializationException? = null
                var poll: NatsConsumerPollObservation? = null
                @NatsListener("listener") fun handle(value: String?, error: NatsSerializationException?, poll: NatsConsumerPollObservation) {
                    this.value = value; this.error = error; this.poll = poll
                }
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        val controller = loadClass("Listeners").getConstructor().newInstance()
        val moduleClass = loadClass("Listeners_NatsListenerModule")
        val module = java.lang.reflect.Proxy.newProxyInstance(moduleClass.classLoader, arrayOf(moduleClass)) { proxy, method, args ->
            java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, *(args ?: emptyArray()))
        }
        val client = org.mockito.Mockito.mock(io.koraframework.nats.common.NatsClient::class.java)
        val config = org.mockito.Mockito.mock(io.koraframework.nats.common.consumer.NatsListenerConfig::class.java, org.mockito.Mockito.CALLS_REAL_METHODS)
        org.mockito.Mockito.`when`(config.subjects()).thenReturn(listOf("events"))
        val decoder = io.koraframework.nats.common.consumer.deserializer.NatsDeserializer<String> {
            throw IllegalArgumentException("bad payload")
        }
        val factory = moduleClass.methods.first { it.name.endsWith("Container") }
        val container = factory.invoke(
            module, controller, client, config,
            io.koraframework.nats.common.consumer.telemetry.impl.NoopNatsConsumerTelemetry.INSTANCE, null, decoder
        )
        val field = io.koraframework.nats.common.consumer.NatsConsumerContainer::class.java.getDeclaredField("recordHandler")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val handler = field.get(container) as io.koraframework.nats.common.consumer.NatsMessageHandler<String>
        val poll = io.koraframework.nats.common.consumer.telemetry.impl.NoopNatsConsumerPollObservation.INSTANCE
        val message = io.nats.client.impl.NatsMessage.builder().subject("events").data(byteArrayOf(1)).build()
        handler.handle(poll, io.koraframework.nats.common.consumer.NatsMessage(message, decoder))
        assertThat(controller.javaClass.getMethod("getValue").invoke(controller)).isNull()
        assertThat(controller.javaClass.getMethod("getError").invoke(controller)).isInstanceOf(io.koraframework.nats.common.exceptions.NatsSerializationException::class.java)
        assertThat(controller.javaClass.getMethod("getPoll").invoke(controller)).isSameAs(poll)
    }

    @Test
    fun nonNullablePayloadCannotBindDecodeError() {
        val result = compile0(
            listOf(NatsSymbolProcessorProvider()), """
            class Listeners { @NatsListener("listener") fun handle(value: String, error: NatsSerializationException?) {} }
        """.trimIndent()
        )
        assertThat(result.assertFailure().messages).anyMatch { it.contains("payload must be nullable") }
    }

    @Test
    fun nestedContracts() {
        compile0(
            listOf(NatsSymbolProcessorProvider()), """
            class Outer {
                @NatsPublisher("publisher")
                interface Events { fun send(message: Message) }
                class Listeners { @NatsListener("listener") fun message(message: Message) {} }
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("\$Outer_Events_Impl")).isNotNull()
        assertThat(loadClass("Outer_Listeners_NatsListenerModule")).isNotNull()
    }

    @Test
    fun tagsOnPayloadAndResponse() {
        compile0(
            listOf(NatsSymbolProcessorProvider()), """
            @NatsPublisher("publisher")
            interface Events {
                @NatsPublisher.Subject("events") @NatsPublisher.Request @Tag(Long::class)
                fun request(@Tag(Int::class) value: String): String
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        val factory = loadClass("Events_PublisherModule").methods.first { it.name.endsWith("PublisherImpl") }
        assertThat(factory.parameters[3].getAnnotation(io.koraframework.common.annotation.Tag::class.java).value.java).isEqualTo(Int::class.java)
        assertThat(factory.parameters[5].getAnnotation(io.koraframework.common.annotation.Tag::class.java).value.java).isEqualTo(Long::class.java)
    }

    @Test
    fun invalidPayloadWithoutSubject() {
        val result = compile0(
            listOf(NatsSymbolProcessorProvider()), """
            @NatsPublisher("publisher") interface Events { fun send(value: String) }
        """.trimIndent()
        )
        assertThat(result.assertFailure().messages).anyMatch { it.contains("requires @NatsPublisher.Subject") }
    }

    @Test
    fun publisherAopProxyIsWired() {
        compile0(
            listOf(NatsSymbolProcessorProvider(), io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider()), """
            @NatsPublisher("publisher")
            interface Events {
                @io.koraframework.logging.common.annotation.Log
                @NatsPublisher.Subject("events") fun send(value: String)
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("\$Events_Impl__AopProxy")).isNotNull()
        assertThat(loadClass("Events_PublisherModule")).isNotNull()
    }

    @Test
    fun explicitMappingSelectsConcreteMapper() {
        compile0(
            listOf(NatsSymbolProcessorProvider()), """
            @NatsPublisher("publisher")
            interface Events {
                @NatsPublisher.Subject("events") fun send(@Mapping(Encoder::class) value: String)
                class Encoder : io.koraframework.nats.common.producer.serializer.NatsSerializer<String> {
                    override fun serialize(subject: String, headers: Headers, value: String): ByteArray = value.toByteArray()
                }
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        val factory = loadClass("Events_PublisherModule").methods.first { it.name.endsWith("PublisherImpl") }
        assertThat(factory.parameterTypes[3]).isEqualTo(loadClass("Events\$Encoder"))
    }

    @Test
    fun applicationGraphResolvesPublisherAndListeners() {
        compile0(
            listOf(
                NatsSymbolProcessorProvider(),
                io.koraframework.config.ksp.processor.ConfigParserSymbolProcessorProvider(),
                io.koraframework.kora.app.ksp.KoraAppProcessorProvider()
            ), """
            @KoraApp
            interface Application : io.koraframework.nats.common.NatsModule,
                io.koraframework.config.common.mapper.ConfigValueMapperModule {
                fun config(): io.koraframework.config.common.Config =
                    io.koraframework.config.common.util.ConfigMappingUtils.fromMap(emptyMap<String, Any>())
                @Root fun root(events: AtomicEvents, other: OtherEvents): Any = events
                @Tag(Events::class)
                fun optionsConfigurer(): io.koraframework.common.Configurer<Options.Builder> =
                    io.koraframework.common.Configurer { options -> options.connectionName("cluster-a") }
                @Component class Listeners {
                    @NatsListener("listener") fun onEvent(value: String) {}
                }
                @Component class OtherListeners {
                    @NatsListener("otherListener") fun onEvent(value: String) {}
                }
                @NatsPublisher("clusterA.publisher") interface Events {
                    @NatsPublisher.Subject("events") fun send(value: String)
                }
                @NatsPublisher("clusterA.atomic") interface AtomicEvents : AtomicBatchPublisher<Events>
                @NatsPublisher("clusterB.publisher") interface OtherEvents {
                    @NatsPublisher.Subject("otherEvents") fun send(value: String)
                }
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("ApplicationGraph")).isNotNull()
        @Suppress("UNCHECKED_CAST")
        val graph = loadClass("ApplicationGraph").constructors[0].newInstance() as java.util.function.Supplier<io.koraframework.application.graph.ApplicationGraphDraw>
        val clients = graph.get().nodes.filter { it.type() == io.koraframework.nats.common.NatsClient::class.java }
        assertThat(clients).hasSize(4)
        assertThat(clients.map { it.tag() }).doesNotContainNull().doesNotHaveDuplicates()
    }

    @Test
    fun generatedAtomicPublisherReusesTypedCodecAndReturnsBatchAck() {
        compile0(
            listOf(NatsSymbolProcessorProvider()), """
            class Contracts {
                @NatsPublisher("publisher") interface Events {
                    @NatsPublisher.Subject("events") fun send(value: String)
                }
                @NatsPublisher("atomic") interface AtomicEvents : AtomicBatchPublisher<Events>
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        val moduleClass = loadClass("Contracts_Events_PublisherModule")
        val module = java.lang.reflect.Proxy.newProxyInstance(moduleClass.classLoader, arrayOf(moduleClass)) { proxy, method, args ->
            java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, *(args ?: emptyArray()))
        }
        val client = org.mockito.Mockito.mock(io.koraframework.nats.common.NatsClient::class.java)
        val connection = org.mockito.Mockito.mock(io.nats.client.Connection::class.java)
        org.mockito.Mockito.`when`(client.connection()).thenReturn(connection)
        val management = org.mockito.Mockito.mock(io.nats.client.JetStreamManagement::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        org.mockito.Mockito.`when`(client.jetStreamManagement()).thenReturn(management)
        org.mockito.Mockito.`when`(management.getStreamInfo("EVENTS").configuration.allowAtomicPublish).thenReturn(true)
        org.mockito.Mockito.`when`(connection.request(org.mockito.ArgumentMatchers.any(io.nats.client.Message::class.java), org.mockito.ArgumentMatchers.any(java.time.Duration::class.java)))
            .thenAnswer { invocation ->
                val sent = invocation.getArgument<io.nats.client.Message>(0)
                val json = if (sent.headers.containsKey("Nats-Batch-Commit")) {
                    "{\"stream\":\"EVENTS\",\"seq\":2,\"batch\":\"${sent.headers.getFirst("Nats-Batch-Id")}\",\"count\":2}"
                } else {
                    ""
                }
                io.nats.client.impl.NatsMessage.builder().subject("reply").data(json.toByteArray()).build()
            }
        val config = org.mockito.Mockito.mock(io.koraframework.nats.common.producer.NatsPublisherConfig::class.java, org.mockito.Mockito.CALLS_REAL_METHODS)
        org.mockito.Mockito.`when`(config.mode()).thenReturn(io.koraframework.nats.common.producer.NatsPublisherConfig.Mode.JETSTREAM)
        val telemetry = io.koraframework.nats.common.producer.telemetry.impl.NoopNatsPublisherTelemetry.INSTANCE
        val encoder = io.koraframework.nats.common.producer.serializer.NatsSerializer<String> { _, _, value -> value.toByteArray() }
        val factory = moduleClass.methods.first { it.name.endsWith("AtomicPublisherFactory") }
            .invoke(module, client, config, telemetry, encoder, io.koraframework.nats.common.producer.NatsPublisherConfig.SubjectConfig { "events.created" })
        val batchConfig = org.mockito.Mockito.mock(io.koraframework.nats.common.producer.NatsAtomicBatchConfig::class.java, org.mockito.Mockito.CALLS_REAL_METHODS)
        org.mockito.Mockito.`when`(batchConfig.stream()).thenReturn("EVENTS")
        val atomic = loadClass("\$Contracts_AtomicEvents_Impl").constructors.single()
            .newInstance(client, config, batchConfig, telemetry, factory) as io.koraframework.nats.common.producer.AtomicBatchPublisher<*>
        atomic.begin().use { batch ->
            val send = loadClass("Contracts\$Events").getMethod("send", String::class.java)
            send.invoke(batch.publisher(), "one"); send.invoke(batch.publisher(), "two")
            org.mockito.Mockito.verifyNoInteractions(connection)
            assertThat(batch.commit().batchSize).isEqualTo(2)
        }
        val sent = org.mockito.ArgumentCaptor.forClass(io.nats.client.Message::class.java)
        org.mockito.Mockito.verify(connection, org.mockito.Mockito.times(2)).request(sent.capture(), org.mockito.ArgumentMatchers.any(java.time.Duration::class.java))
        assertThat(sent.allValues.first().data).isEqualTo("one".toByteArray())
        assertThat(sent.allValues.map { it.subject }).containsOnly("events.created")
    }

    @Test
    fun atomicPublisherRejectsPerMessageAcknowledgements() {
        val result = compile0(
            listOf(NatsSymbolProcessorProvider()), """
            class Contracts {
                @NatsPublisher("publisher") interface Events { fun send(message: Message): PublishAck }
                @NatsPublisher("atomic") interface AtomicEvents : AtomicBatchPublisher<Events>
            }
        """.trimIndent()
        )
        assertThat(result.assertFailure().messages).anyMatch { it.contains("Atomic batch publisher methods must return Unit") }
    }

    @Test
    fun atomicPublisherFactoryRetainsPublisherAop() {
        compile0(
            listOf(NatsSymbolProcessorProvider(), io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider()), """
            class Contracts {
                @NatsPublisher("publisher") interface Events {
                    @io.koraframework.logging.common.annotation.Log
                    @NatsPublisher.Subject("events") fun send(value: String)
                }
                @NatsPublisher("atomic") interface AtomicEvents : AtomicBatchPublisher<Events>
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        val methods = loadClass("Contracts_Events_PublisherModule").methods
        val normal = methods.first { it.name.endsWith("PublisherImpl") }
        val atomic = methods.first { it.name.endsWith("AtomicPublisherFactory") }
        assertThat(atomic.parameterTypes).containsExactly(*normal.parameterTypes)
        assertThat(atomic.parameterCount).isGreaterThan(5)
    }
}
