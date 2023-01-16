# Description

Модуль предоставляет реализацию валидации для объектов с помощью аннотаций.

# Dependency

**Java**:
```groovy
annotationProcessor "ru.tinkoff.kora:validation:validation-annotation-processor"
implementation "ru.tinkoff.kora:validation:validation-common"
```

**Kotlin**:
```groovy
ksp "ru.tinkoff.kora:validation:validation-symbol-processor"
implementation "ru.tinkoff.kora:validation:validation-common"
```

# Getting Started

Предположим имеется Java Record:
```java
public record Foo(String number,
                  Long code,
                  OffsetDateTime timestamp,
                  Bar bar) {}
```

Для того чтобы для объекта был сгенерирован `Validator`, требуется проаннотировать его `@Validated`:
```java
@Validated
public record Foo(String number,
                  Long code,
                  OffsetDateTime timestamp,
                  Bar bar) {}
```

### Nullable vs NotNull

Предполагается что все поля по дефолту являются `NotNull`, значит для всех них будут сгенерированы `NotNull` проверки в `Validator`.

Чтобы указать поле как не обязательное, требуется пометить его любой `@Nullable` аннотацией,
для такого поля не будет сгенерирована проверка на *null*:
```java
@Validated
public record Foo(String number,
                  Long code,
                  @Nullable OffsetDateTime timestamp,
                  Bar bar) {}
```

### Record vs Class

Для Record классов используется синтаксис доступа к полям через Record-like контракты геттеров, в случае `Foo` и поля `code` будет использоваться *getter* `code()` в сгенерированном `Validator`

Для обычного класса ожидается что будет использоваться синтаксис Java *Getters*, пример для:
```java
@Validated
public class Bar {
    
    @NotEmpty
    private String id;

    public String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }
}
```

Будет использоваться *accessor* вида `getId()` в сгенерированном `Validator`, *getter* должен иметь минимум *package-private* видимость.

### Annotations

Предполагается использовать для валидации полей набор аннотаций валидации из пакета `ru.tinkoff.kora.validation.annotation` такие как `@NotEmpty`, `@Pattern`, etc.

Пример размеченного для валидации объекта выглядит так:
```java
@Validated
public record Foo(@Pattern("\\d+") String number,
                  @Range(from = 1, to = 10) Long code,
                  @Nullable OffsetDateTime timestamp,
                  Bar bar) {}
```

Доступные аннотации валидации:
- `@NotEmpty` - Проверяет что строка не пустая
- `@NotBlank` - Проверяет что строка не состоит из пустых символов
- `@Pattern` - Проверяет соответствие Regular Expression (RegEx)
- `@Range` - Проверяет что число находится в заданном диапазоне
- `@Size` - Проверяет что коллекция (List, Set, Map) имеет размер в заданном диапазоне

### Inner Validation

Для валидации полей сложных объектов для которых сгенерированы валидаторы (или он будет предоставлен в ручную) также требуется указать `@Validated` аннотацию:
```java
@Validated
public record Foo(String number,
                  Long code,
                  OffsetDateTime timestamp,
                  @Validated Bar bar) {}
```

В данном случае в сгенерированном валидаторе будет вызван валидатор для `Bar` типа, 
в случае если тип `Bar` также проаннотирован `@Validated` то `Validator<Bar>` будет сгенерирован для него, 
в противном случае требуется предоставить самостоятельную реализацию в контейнер Bean'ов.

### Validator

Предполагается что для размеченной сущности будет сгенерирован и зарегистрирован Bean `Validator` с сигнатурой сущности, 
в случае `Foo` будет зарегистрирован Bean `Validator<Foo>`.

#### Options

По дефолту используется *full* валидация, то есть проверяются и собираются все возможные ошибки валидации.

Для Fail Fast валидации следует указать соответствующую опцию:
```java
final ValidatorContext context = ValidationContext.builder().failFast(true).build()
final List<Violation> violations = fooValidator.validate(value, context);
```

### Kotlin

Все контракты и примеры Java актуальны для Kotlin, отличие лишь в NotNull / Nullable, это определяется синтаксисом Kotlin и не требует валидации со стороны `Validator`

### Custom Annotation

Для создания кастомной аннотации требуется:
1) Создать реализацию Validator, который будет наследовать `Validator`:
```java
final class MyValidStringValidator implements Validator<String> {

    @NotNull
    @Override
    public List<Violation> validate(String value, @NotNull ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be not empty, but was null"));
        } else if (value.isEmpty()) {
            return List.of(context.violates("Should be not empty, but was empty"));
        }

        return Collections.emptyList();
    }
}
```

2) Создать Interface, который будет наследовать `ValidatorFactory`:
```java
public interface MyValidValidatorFactory<T> extends ValidatorFactory<T> { }
```

3) Зарегистрировать Bean для `ValidatorFactory` для требуемых типов:
```java
@Module
public interface ValidationModule {
    default MyValidValidatorFactory<String> myValidStringConstraintFactory() {
        return MyValidStringValidator::new;
    }
}
```

4) Создать аннотацию валидации и проаннотировать ее `@ValidatedBy` с ранее созданной `ValidatorFactory`:
```java
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.FIELD})
@ValidatedBy(MyValidValidatorFactory.class)
public @interface MyValid {
    
}
```

5) Проаннотировать сущность, которая требует валидации:
```java
@Validated
public record Foo(@MyValid String number) {}
```
