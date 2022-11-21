# Kafka

## Подключение

```groovy
implemenation 'ru.tinkoff.kora:kafka'
```

## Контейнер для KafkaConsumer

Kora предоставляет небольшую обёртку над `KafkaConsumer`, позволяющую легко запустить обработку входящих событий.
Конструктор контейнера выглядит следующим образом:

```java
public KafkaConsumerContainer(KafkaConsumerConfig config,
                              Deserializer<K> keyDeserializer,
                              Deserializer<V> valueDeserializer,
                              BaseKafkaRecordsHandler<K, V> handler) {
    this.factory = new KafkaConsumerFactory<>(config);
    this.handler = handler;
    this.keyDeserializer = keyDeserializer;
    this.valueDeserializer = valueDeserializer;
    this.config = config;
}
```

`BaseKafkaRecordsHandler<K,V>` это базовый функциональный интерфейс обработчика:

```java
package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@FunctionalInterface
public interface BaseKafkaRecordsHandler<K, V> {
    void handle(ConsumerRecords<K, V> records, KafkaConsumer<K, V> consumer);
}

```
`KafkaConsumerConfig` - обёртка над используемым KafkaConsumer `Properties`:

```java
public record KafkaConsumerConfig(
    Properties driverProperties,
    @Nullable List<String> topics,
    @Nullable Pattern topicsPattern,
    @Nullable List<String> partitions,
    Either<Duration, String> offset,
    Duration pollTimeout,
    int threads
) {
}
```

* driverProperties - `Properties` из официального клиента кафки, документацию по ним можно посмотреть по ссылке: [https://kafka.apache.org/documentation/#consumerconfigs](https://kafka.apache.org/documentation/#consumerconfigs)
* topics - список топиков на которые нужно подписаться, через запятую
* topicsPattern - регулярка, по которой можно подписаться на топики.
* partitions - список партиций к которым нужно подключиться через assign, в формате `{topic}:{partition_number}`, например `books:1,books:2,books:5`
* offset - стратегия, которую нужно применить при подключении через assign.  
  Допустимые значение `earliest` - перейти на самый ранний доступный offset, `latest` - перейти на последний доступный offset, строка в формате `Duration`, например `5m` - сдвиг на определённое время назад  
* pollTimeout - таймаут для poll(), значение по умолчанию - 5 секунд
* threads - количество потоков, выделенных на консюмеры. 1 поток = 1 консюмер группы, значение по умолчанию - 1


Пример конфигурации для подписки на топики:
```
kafka {
    first {
        pollTimeout: 3s
        topics: "first,second,third"
        driverProperties {
             "bootstrap.servers": "localhost:9092"
             "group.id": "some_consumer_group"
        }
    }
}
```

Пример конфигурации для подключения к топикам без группы. В этом примере консьюмер будет подключен ко всем партициям в топике и офсет сдвинут на 10 минут назад.
```
kafka {
    first {
        pollTimeout: 3s,
        topics: "first",
        offset: 10m
        driverProperties {
             "bootstrap.servers": "localhost:9092"
        }
    }
}
```

### Генерация по аннотации

В большинстве случаев проще всего будет воспользоваться аннотацией `@KafkaIncoming`, например, как в коде ниже:
```java
@Component
final class Consumers {
    @KafkaIncoming("kafka.first")
    void processRecord(ConsumerRecord<String, String> record) { 
        //some handler code
    }
}
```
На этапе компиляции будет сгенерирован модуль `ConsumersModule`, отмеченный аннотацией `@Module`(подробнее про это можно почитать [здесь](/features/container/#module)),

Пример сгенерированного модуля:
```java
@Module
public interface ConsumersModule {
    default KafkaConsumerContainer<String, String> processRecord(
        Consumers _controller,
        KafkaConsumerConfig _consumerConfig,
        Deserializer<String> keyDeserializer, Deserializer<String> valueDeserializer) {
        return new KafkaConsumerContainer<>(
            _consumerConfig,
            keyDeserializer,
            valueDeserializer,
            HandlerWrapper.wrapHandler(_controller::processRecordWithConsumer)
        );
    }
}
```

`HandlerWrapper` приводит контроллеры к базовому `BaseKafkaRecordsHandler`, при необходимости добавляя автоматический коммит.
Так как `KafkaConsumerContainer` является реализацией `Lifecycle`, при запуске он будет инициализирован. В данном случае - подпишется на указанные топики и запустит poll loop с вызовом обработчика.
Подробнее про компоненты с жизненным циклом можно прочитать [в соответствующем разделе](/features/container/#lifecycle) документации.

### Конфигурирование консюмеров

В случае, если нужно разное поведение для разных топиков, существует возможность создавать несколько подобных контейнеров, каждый со своим индивидуальным конфигом. Выглядит это примерно так:

```java
@Component
final class Consumers {
    @KafkaIncoming("kafka.first")
    void processRecord(ConsumerRecord<String, String> record) { 
        //some handler code
    }
    @KafkaIncoming("kafka.other")
    void processRecords(ConsumerRecords<String, String> records, Consumer<String,String> consumer) {
        //some handler code
        consumer.commitAsync();
    }
}
```
Значение в аннотации указывает, из какой части файла конфигурации нужно брать настройки. В том, что касается получения конфигурации — работает аналогично `@ConfigSource`

### Поддерживаемые сигнатуры:
```java
@KafkaIncoming("kafka.first")
void processRecordsWithConsumer(ConsumerRecords<String, CustomEvent> records, Consumer<String, CustomEvent> consumer) {}
```
Принимает `ConsumerRecords` и `Consumer`, коммитить оффсет нужно вручную.

```java
@KafkaIncoming("kafka.first")
void processRecordWithConsumer(ConsumerRecord<String, String> records, Consumer<String, String> consumer) {}
```
Принимает `ConsumerRecord` и `Consumer`. Как и в предыдущем случае, `commit` нужно вызывать вручную. Вызывается для каждого `ConsumerRecord` полученного при вызове `poll()`

```java
@KafkaIncoming("kafka.first"
void processRecords(ConsumerRecords<String, String> records) {}
```
Принимает `ConsumerRecords`, после вызова обработчика вызывается `commitSync()`.

```java
@KafkaIncoming("kafka.first")
void processRecord(ConsumerRecord<String, String> record) {}
```
Принимает `ConsumerRecord`, после обработки всех `ConsumerRecord` вызывается `commitSync()`.

```java
@KafkaIncoming("kafka.first")
void processValue(CustomEvent value) {}
```

Принимает `ConsumerRecord.value`, после обработки всех событий вызывается `commitSync()`.

```java
@KafkaIncoming("kafka.first")
void processKeyValue(String key, CustomEvent value) {}
```
То же, что и предыдущий кейс, но добавляется key из `ConsumerRecord`

### Исключения в обработчике

Если метод помеченный `@KafkaIncoming` выбросит исключение, то Consumer будет перезапущен, потому что нет общего решения, как реагировать на это и разработчик **должен** сам решить как эту ситуацию обрабатывать.

### Обработка ошибок десериализации

Если вы используете сигнатуру с `ConsumerRecord` или `ConsumerRecords`, то вы получите исключение десериализации значения в момент вызова методов `key` или `value`. 
В этот момент стоит его обработать нужным вам образом.  
Выбрасываются следующие исключения:

* `ru.tinkoff.kora.kafka.common.exceptions.RecordKeyDeserializationException`
* `ru.tinkoff.kora.kafka.common.exceptions.RecordValueDeserializationException`

Из этих исключений можно получить сырой `ConsumerRecord<byte[], byte[]>`

Если вы используете сигнатуру с распакованными `key`/`value`, то можно добавить последним аргументом `Exception`

```java
@KafkaIncoming("kafka.first")
public void process(@Nullable String key, @Nullable String value, @Nullable Exception exception) {
    if (exception != null) {
      //handle exception
    } else {
      //handle key/value
    }
}
```

Обратите внимание, что все аргументы становятся необязательными, то есть мы ожидаем что у нас либо будут ключ и значение, либо исключение


### Прочее

Для обработчиков, не использующих ключ, по умолчанию используется `Deserializer<byte[]>` т.к. он просто возвращает не обработанные байты.





