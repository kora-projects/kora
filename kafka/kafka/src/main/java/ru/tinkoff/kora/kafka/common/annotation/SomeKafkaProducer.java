package ru.tinkoff.kora.kafka.common.annotation;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.Deserializer;
import ru.tinkoff.kora.common.Module;
import ru.tinkoff.kora.common.Tag;

//@KafkaProducer("some-config-path")
//public interface SomeKafkaProducer extends Producer<String, ProtobufGeneratedClass> {
//
//    @Module
//    interface SomewModule {
//        default SomeKafkaProducer producerNoTag(Deserializer<byte[]> key, Deserializer<byte[]> value) {
//            // generated class
//            throw new RuntimeException();
//        }
//
//        @Tag(SomeKafkaProducer.class)
//        default Transactional<byte[], byte[]> producerNoTagTx(Deserializer<byte[]> key, Deserializer<byte[]> value) {
//            // generated class
//            throw new RuntimeException();
//        }
//
//        @Tag(SomeKafkaProducer.class)
//        default SomeKafkaProducer producerNoTag(SomeKafkaProducer noTag) {
//            // generated class
//            return noTag;
//        }
//    }
//
//    interface Transactional<K, V> {
//        Transaction<K, V> tx();
//
//        interface Transaction<K, V> extends AutoCloseable, Producer<K, V> {}
//    }
//}
