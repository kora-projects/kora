package io.koraframework.kafka.avro.serializer;

import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;
import io.koraframework.config.common.annotation.ConfigMapper;

/**
 * <b>Русский</b>: Конфигурация Avro сериализатора Kafka (реестр схем).
 * <hr>
 * <b>English</b>: Configuration of the Kafka Avro serializer (Schema Registry).
 */
@ConfigMapper
public interface KafkaAvroSerializerConfig {

    /**
     * <b>Русский</b>: Автоматически регистрировать схему в реестре при сериализации.
     * Когда {@code false}, идентификатор берётся из уже зарегистрированной схемы.
     * <hr>
     * <b>English</b>: Automatically register the schema in the registry on serialization.
     * When {@code false}, the id is looked up from an already registered schema.
     */
    default boolean autoRegisterSchemas() {
        return true;
    }

    /**
     * <b>Русский</b>: Стратегия именования субъекта (subject) в реестре схем.
     * <hr>
     * <b>English</b>: Subject naming strategy used against the Schema Registry.
     */
    default SubjectNameStrategyType subjectNameStrategy() {
        return SubjectNameStrategyType.TOPIC_NAME;
    }

    enum SubjectNameStrategyType {
        TOPIC_NAME,
        RECORD_NAME,
        TOPIC_RECORD_NAME;

        public SubjectNameStrategy create() {
            return switch (this) {
                case TOPIC_NAME -> new TopicNameStrategy();
                case RECORD_NAME -> new RecordNameStrategy();
                case TOPIC_RECORD_NAME -> new TopicRecordNameStrategy();
            };
        }
    }
}
