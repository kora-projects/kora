# Kafka

Предоставляет модуль для интеграции с [Apache Kafka](https://kafka.apache.org/) для создания `Consumer` & `Producer`.

Оглавление:
- [Зависимости](#зависимости)
- [Kafka Consumer](#kafka-consumer)
  - [@KafkaListener](#аннотация-kafkalistener)
    - [Конфигурация](#конфигурация-kafkalistener)
    - [Сигнатуры методов](#сигнатуры-методов-kafkalistener)
    - [Десериализация](#настройка-десериализации)
    - [Обработка ошибок](#обработка-исключений-kafkalistener)
      - [Ошибки десериализации](#ошибки-десериализации)
    - [Детали реализации](#детали-реализации-kafkalistener)
  - [Контейнер](#контейнер-для-kafkaconsumer)
- [Kafka Producer](#kafka-producer)
  - [@KafkaPublisher](#аннотация-kafkapublisher)
    - [@KafkaPublisher Topic](#аннотация-kafkapublishertopic)
    - [Конфигурация](#конфигурация-kafkapublisher)
    - [Сигнатуры методов](#сигнатуры-методов-kafkapublisher)
    - [Сигнатуры методов](#настройка-сериализации)
    - [Обработка ошибок](#обработка-исключений-kafkapublisher)
      - [Ошибки сериализации](#ошибки-сериализации)
  - [@KafkaPublisher Transactional](#аннотация-kafkapublishertransactional)
  - [Контейнер](#контейнер-для-kafkaproducer)

## Зависимости

**Java**:
```groovy
annotationProcessor "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:kafka"
```

**Kotlin**:
```groovy
ksp "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:kafka"
```

### Модуль

```java
@KoraApp
public interface ApplicationModules extends KafkaModule { }
```

## Kafka Consumer

Описания работы с [Kafka Consumer](https://docs.confluent.io/platform/current/clients/consumer.html)

### Аннотация @KafkaListener

Для создания `Consumer` требуется использовать аннотацию `@KafkaListener` над методом:
```java
@Component
final class Consumers {
    
    @KafkaListener("path.to.consumer.config")
    void processRecord(String key, String value) { 
        // my code
    }
}
```

Параметр аннотации `@KafkaListener` указывает на путь к [конфигурации](#конфигурация-kafkalistener) `Consumer`'а.

В случае, если нужно разное поведение для разных топиков, существует возможность создавать несколько подобных контейнеров, каждый со своим индивидуальным конфигом.
Выглядит это примерно так:

```java
@Component
final class Consumers {
    
    @KafkaListener("path.to.consumer.config.first")
    void processFirst(String key, String value) { 
        // some handler code
    }
    
    @KafkaListener("path.to.consumer.config.second")
    void processSecond(String key, String value) {
        // some handler code
    }
}
```
Значение в аннотации указывает, из какой части файла конфигурации нужно брать настройки. В том, что касается получения конфигурации — работает аналогично `@ConfigSource`

#### Конфигурация @KafkaListener

`KafkaListenerConfig` - обёртка над используемым KafkaConsumer `Properties` и используется для конфигурации `@KafkaListener`:

```java
public interface KafkaListenerConfig {
    
    Properties driverProperties();

    @Nullable
    List<String> topics();

    @Nullable
    Pattern topicsPattern();

    @Nullable
    List<String> partitions();

    default Either<Duration, String> offset() { return Either.right("latest"); }

    default Duration pollTimeout() { return Duration.ofSeconds(5); }

    default Duration backoffTimeout() { return Duration.ofSeconds(15); }

    default int threads() { return 1; }

    default Duration partitionRefreshInterval() { return Duration.ofMinutes(1); }
}
```

Настройки конфигурации:
* `driverProperties` - *Properties* из официального клиента кафки, документацию по ним можно посмотреть по ссылке: [https://kafka.apache.org/documentation/#consumerconfigs](https://kafka.apache.org/documentation/#consumerconfigs)
* `topics` - список топиков на которые нужно подписаться, через запятую
* `topicsPattern` - регулярка, по которой можно подписаться на топики.
* `offset` - стратегия, которую нужно применить при подключении через assign.  
  Допустимые значение *earliest* - перейти на самый ранний доступный offset, *latest* - перейти на последний доступный offset, строка в формате `Duration`, например `5m` - сдвиг на определённое время назад
* `pollTimeout` - таймаут для poll(), значение по умолчанию - 5 секунд
* `threads` - количество потоков, выделенных на консюмеры. 1 поток = 1 консюмер группы, значение по умолчанию - 1

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

Пример конфигурации для подключения к топикам без группы. 
В этом примере Consumer будет подключен ко всем партициям в топике и офсет сдвинут на 10 минут назад.

В случае подключения без `group.id` можно указывать только 1 топик.
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

#### Сигнатуры методов @KafkaListener

Принимает `value` из `ConsumerRecord`, после обработки всех событий вызывается `commitSync()`:
```java
@KafkaListener("path.to.consumer.config")
void processValue(String value) {
    // some handler code
}
```

Аналогично предыдущему, но добавляется `Key` из `ConsumerRecord`:
```java
@KafkaListener("path.to.consumer.config")
void processKeyValue(String key, String value) {
    // some handler code
}
```

Аналогично предыдущему, но добавляется `Headers` из `ConsumerRecord`:
```java
@KafkaListener("path.to.consumer.config")
void processKeyValue(String key, String value, Headers headers) {
    // some handler code
}
```

Принимает `ConsumerRecords`, после вызова обработчика вызывается `commitSync()`:
```java
@KafkaListener("path.to.consumer.config")
void processRecords(ConsumerRecords<String, String> records) {
    // some handler code
}
```

Принимает `ConsumerRecord`, после обработки всех `ConsumerRecord` вызывается `commitSync()`:
```java
@KafkaListener("path.to.consumer.config")
void processRecord(ConsumerRecord<String, String> record) {
    // some handler code
}
```

Принимает `ConsumerRecords` и `Consumer`, коммитить оффсет нужно вручную:
```java
@KafkaListener("path.to.consumer.config")
void processRecordsWithConsumer(ConsumerRecords<String, String> records, Consumer<String, String> consumer) {
    // some handler code
}
```

Принимает `ConsumerRecord` и `Consumer`. 
Как и в предыдущем случае, `commit` нужно вызывать вручную. 
Вызывается для каждого `ConsumerRecord` полученного при вызове `poll()`:
```java
@KafkaListener("path.to.consumer.config")
void processRecordWithConsumer(ConsumerRecord<String, String> records, Consumer<String, String> consumer) {
    // some handler code
}
```

#### Настройка десериализации

`Deserializer` - используется для десериализации ключей и значений `ConsumerRecord`.

Для более точной настройки `Deserializer` поддерживаются теги.
Теги можно установить на параметре-ключе, параметре-значении, а так же на параметрах типа `ConsumerRecord` и `ConsumerRecords`.
Эти теги будут установлены на зависимостях контейнера.

Примеры:
```java
@Component
final class Consumers {

    @KafkaListener("path.to.consumer.config")
    void process1(@Tag(Sometag1.class) String key, @Tag(Sometag2.class) String value) {
        // some handler code
    }

    @KafkaListener("path.to.consumer.config")
    void process2(ConsumerRecord<@Tag(Sometag1.class) String, @Tag(Sometag2.class) String> record) {
        // some handler code
    }

    @KafkaListener("path.to.consumer.config")
    void process2(ConsumerRecords<@Tag(Sometag1.class) String, @Tag(Sometag2.class) String> record) {
        // some handler code
    }
}
```

#### Обработка исключений @KafkaListener

Если метод помеченный `@KafkaListener` выбросит исключение, то Consumer будет перезапущен, 
потому что нет общего решения, как реагировать на это и разработчик **должен** сам решить как эту ситуацию обрабатывать.

##### Ошибки десериализации

Если вы используете сигнатуру с `ConsumerRecord` или `ConsumerRecords`, то вы получите исключение десериализации значения в момент вызова методов `key` или `value`.
В этот момент стоит его обработать нужным вам образом.  

Выбрасываются следующие исключения:
* `ru.tinkoff.kora.kafka.common.exceptions.RecordKeyDeserializationException`
* `ru.tinkoff.kora.kafka.common.exceptions.RecordValueDeserializationException`

Из этих исключений можно получить сырой `ConsumerRecord<byte[], byte[]>`.

Если вы используете сигнатуру с распакованными `key`/`value`/`headers`, то можно добавить последним аргументом `Exception`, `Throwable`, `RecordKeyDeserializationException`
или `RecordValueDeserializationException`.

```java
@Component
final class Consumers {

    @KafkaListener("path.to.consumer.config")
    public void process(@Nullable String key, @Nullable String value, @Nullable Exception exception) {
        if (exception != null) {
            //handle exception
        } else {
            //handle key/value
        }
    }
}
```

Обратите внимание, что все аргументы становятся необязательными, то есть мы ожидаем что у нас либо будут ключ и значение, либо исключение.

#### Детали реализации @KafkaListener

На этапе компиляции будет сгенерирован модуль `ConsumersModule`, отмеченный аннотацией `@Module`(подробнее про это можно почитать [здесь](/features/container/#module)),

Пример сгенерированного модуля:
```java
@Module
public interface ConsumersModule {
    default KafkaConsumerContainer<String, String> processRecord(
        Consumers _controller,
        KafkaListenerConfig _consumerConfig,
        Deserializer<String> keyDeserializer, Deserializer<String> valueDeserializer) {
        return new KafkaSubscribeConsumerContainer<>(
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

### Контейнер для KafkaConsumer

Kora предоставляет небольшую обёртку над `KafkaConsumer`, позволяющую легко запустить обработку входящих событий.

Конструктор контейнера выглядит следующим образом:
```java
public KafkaSubscribeConsumerContainer(KafkaListenerConfig config,
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
@FunctionalInterface
public interface BaseKafkaRecordsHandler<K, V> {
    void handle(ConsumerRecords<K, V> records, KafkaConsumer<K, V> consumer);
}
```

## Kafka Producer

Описания работы с [Kafka Producer](https://docs.confluent.io/platform/current/clients/producer.html)

### Аннотация @KafkaPublisher

Предполагается использовать аннотацию `@KafkaPublisher` на интерфейсе для создания `Kafka Publisher`:
```java
@KafkaPublisher("path.to.consumer.config")
public interface TestPublisher {
    Future<?> send(ProducerRecord record);
}
```

Параметр аннотации указывает на путь до [конфигурации](#конфигурация-kafkapublisher).

#### Конфигурация @KafkaPublisher

`KafkaPublisherConfig` - обёртка над используемым KafkaProducer `Properties` и используется для конфигурации `@KafkaPublisher`:

```java
@ConfigValueExtractor
public interface KafkaPublisherConfig {

    Properties driverProperties();
}
```

Настройки конфигурации:
* `driverProperties` - *Properties* из официального клиента кафки, документацию по ним можно посмотреть по ссылке: [https://kafka.apache.org/documentation/#producerconfigs](https://kafka.apache.org/documentation/#producerconfigs)

#### Аннотация @KafkaPublisher.Topic

В случае если требуется использовать типизированные контракты на определенные топики то предполагается использование аннотации `@KafkaPublisher.Topic`
для создания таких контрактов:

```java
@KafkaPublisher("path.to.producer.config")
public interface TestProducer {

    @KafkaPublisher.Topic("path.to.topic.config")
    void send(String value);
}
```

Параметр аннотации указывает на путь для конфигурации топика:
```java
public interface TopicConfig {

    String topic();

    @Nullable
    Integer partition();
}
```
Если значение начинается с символа `.`, то путь конкатенируется со значанием из анноации `@KafkaPublisher`.

Настройки конфигурации:
* `topic` - в какой топик метод будет отправлять данные
* `partition` - в какой partition топика метод будет отправлять данные (опционально)


#### Сигнатуры методов @KafkaPublisher

Отправляет `value` от `ProducerRecord`:
```java
@KafkaPublisher("path.to.producer.config")
public interface TestProducer {

    @KafkaPublisher.Topic("path.to.topic.config")
    void send(String value);
}
```

Отправляет `value` & `key` от `ProducerRecord`:
```java
@KafkaPublisher("path.to.producer.config")
public interface TestProducer {

    @KafkaPublisher.Topic("path.to.topic.config")
    void send(String key, String value);
}
```

Отправляет `value` & `key` * `headers` от `ProducerRecord`:
```java
@KafkaPublisher("path.to.producer.config")
public interface TestProducer {

    @KafkaPublisher.Topic("path.to.topic.config")
    void send(String key, String value, Headers headers);
}
```

Можно получать как результат операции `RecordMetadata`:
```java
@KafkaPublisher("path.to.producer.config")
public interface TestProducer {

    @KafkaPublisher.Topic("path.to.topic.config")
    RecordMetadata send(String value);
}
```

Можно получать как результат операции `Future<RecordMetadata>`:
```java
@KafkaPublisher("path.to.producer.config")
public interface TestProducer {

    @KafkaPublisher.Topic("path.to.topic.config")
    Future<RecordMetadata> send(String value);
}
```

Можно передать `Callback`:
```java
@KafkaPublisher("path.to.producer.config")
public interface TestProducer {

    @KafkaPublisher.Topic("path.to.topic.config")
    void send(String value, Callback callback);
}
```

#### Настройка сериализации

Для уточнения какой `Serializer` взять из контейнера есть возможность использовать теги.

Теги необходимо устанавливать на параметры `Producer` или `key`/`value` методов:
```java
@KafkaPublisher("path.to.producer.config")
public interface MyKafkaProducer {

    @KafkaPublisher.Topic("path.to.topic.config")
    void send(@Tag(MyTag3.class) String key, @Tag(MyTag4.class) String value);
}
```

### Транзакции

Возможно отправлять сообщение в Kafka в [рамках транзакции](https://www.confluent.io/blog/transactions-apache-kafka/), для этого предполагается использовать
аннотацию `@KafkaPublisher` на интерфейсе, наследующем `TransactionalPublisher`, параметром у которого указать отмеченный
`@KafkaPublisher` обычный publisher.

Требуется сначала создать обычного `KafkaProducer` а затем его использовать для создания транзакционного Producer'а:
```java
@KafkaPublisher("path.to.producer.config")
public interface TestPublisher {

    @KafkaPublisher.Topic("path.to.topic.config")
    void send(String key, String value);
}

@KafkaPublisher.Transactional("path.to.transactional.producer.config")
public interface TransactionalPublisher extends ru.tinkoff.kora.kafka.common.producer.TransactionalPublisher<TestProducer> {

}
```

Предполагается использовать методы `inTx` для отправки таких сообщений, все сообщения в рамках Lambda будут применены в случае успешного ее выполнения и отменены в случае ошибки.
```java
transactionalPublisher.inTx(producer -> {
    producer.send("key", "value");
    producer.send("key", "value");
});
```
