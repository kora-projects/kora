# Json

Kora предоставляет набор аннотаций для генерации JsonReader/JsonWriter в Compile Time.

## Kora

Dependency:
```groovy
annotationProcessor "ru.tinkoff.kora:json-annotation-processor"
implementation "ru.tinkoff.kora:json-module"
```

Module:
```java
@KoraApp
public interface ApplicationModules extends JsonModule { }
```

## Jackson

Dependency:
```groovy
annotationProcessor "ru.tinkoff.kora:json-annotation-processor"
implementation "ru.tinkoff.kora:jackson-module"
```

Module:
```java
@KoraApp
public interface ApplicationModules extends JacksonModule { }
```

## Getting Started

```java
@Json
public record DtoWithNullableFields(
    @JsonField("field_1") String field1,
    @Nullable String field2,
    @Nullable String field3,
    @JsonSkip int field4
) {}
```

* Аннотация `@Json` указывает на то, что для объекта нужно создать реализацию `JsonReader` и `JsonWriter`
* Аннотация `@JsonField` указывает что поле `field_1` в json-строке будет соответствовать `field1` в dto
* `@Nullable` - показывает, что значение может быть null
* `@JsonSkip` - указание пропустить поле при создании JsonWriter

Кроме аннотации `@Json` можно воспользоваться `@JsonReader` или `JsonWriter`, это 
актуально для тех случаев, когда нужно создать только Reader или Writer соответственно

## Поддерживаемые типы

* UUID
* String
* Boolean
* boolean
* Integer
* int
* BigInteger
* BigDecimal
* Double
* double
* Float
* float
* Long
* long
* Short
* short
* byte[]
* List<Integer>
* Set<Integer>
* LocalDate
* LocalTime
* LocalDateTime
* OffsetTime
* OffsetDateTime
* ZonedDateTime
* Year
* YearMonth
* MonthDay
* Month
* DayOfWeek
* ZoneId
* Duration

## Поддержка sealed классов и интерфейсов

Для поддержки sealed классов добавлены две аннотации:
1. `@JsonDiscriminatorField` определяет поле дискриминатора в json-объекте, вешается на sealed класс/интерфейс
2. `@JsonDiscriminatorValue` определяет значение для вышеуказанного поля, вешается на класс-наследник sealed класса/интерфейса

Пример:
```java
@Json
@JsonDiscriminatorField("@type")
public sealed interface Event {
    @JsonDiscriminatorValue("first-type-event")
    final record FirstTypeEvent(String id, SomeData data) implements Event {}
    @JsonDiscriminatorValue("second-type-event")
    final record SecondTypeEvent(String id, OtherData data) implements Event {}
    @JsonDiscriminatorValue("third-type-event")
    record ThirdTypeEvent(String id, ThirdData data) implements Event {}
}
```

Для классов-наследников reader и writer генерируются по тем же правилам, как если бы на них была аннотация json, + генерируется reader/writer для самого sealed класса/интерфейса. 

## Использование в других модулях
При использовании некоторых модулей необязательно добавлять аннотацию `@Json` к dto. Например, в примере с http-client был использован модуль `HttpClientJsonModule`.
В [коде](/kora/features/http-client/#client_gen) использовался dto `Greeting`, для которого были сгенерированы `JsonReader<Greeting>` и `JsonWriter<Greeting>`:

