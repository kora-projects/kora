# Description

Модуль предоставляет реализацию валидации для объектов с помощью аннотаций.

# Dependency

**Java**:

```groovy
annotationProcessor "ru.tinkoff.kora:validation-annotation-processor"
implementation "ru.tinkoff.kora:validation-common"
```

**Kotlin**:

```groovy
ksp "ru.tinkoff.kora:validation-symbol-processor"
implementation "ru.tinkoff.kora:validation-common"
```

## Validation

Для валидации используются предоставленные фреймворком аннотации, также есть возможность создавать на базе этого свои собственные [аннотации валидации](#create-custom-annotation).

Основные аннотации:

- `@Valid` - используется для разметки валидации параметра/поля/результата, требует `Validator<T>` который будет предоставлен как Bean.
  В случае если `@Valid` проаннотирован Class или Record, то `Validator<T>` для этого Class/Record будет сгенерирован фреймворком.

- `@ValidatedBy` - используется для создания аннотаций валидации, объявляет фабрику `ValidatorFactory`.

- `@Size \ @Range \ @NotEmpty \ etc` - используются для разметки валидации параметра/поля/результата, требует `Validator<T>` который будет предоставлен по средствам `ValidatorFactory` объявленным в рамках `@ValidatedBy`.

- `@Validate` - декларирует что метод требует `AOP` валидации параметров/результата.

### Standard Validation

Предполагается использовать для валидации полей набор аннотаций валидации из пакета `ru.tinkoff.kora.validation.common.annotation` такие как `@NotEmpty`, `@Pattern`, etc.

Пример размеченного для валидации объекта выглядит так:
```java
@Valid
public record Foo(@Pattern("\\d+") String number,
                  @Range(from = 1, to = 10) Long code,
                  OffsetDateTime timestamp,
                  Bar bar) { }

@Valid
public record Bar(@Pattern("\\d+") String number) {}
```

Доступные аннотации валидации:

- `@NotEmpty` - Проверяет что строка не пустая
- `@NotBlank` - Проверяет что строка не состоит из пустых символов
- `@Pattern` - Проверяет соответствие Regular Expression (RegEx)
- `@Range` - Проверяет что число находится в заданном диапазоне
- `@Size` - Проверяет что коллекция (List, Set, Map) имеет размер в заданном диапазоне

### Non-Standard Validation

Для валидации полей сложных объектов для которых сгенерированы валидаторы (или он будет предоставлен в ручную) также требуется указать `@Valid` аннотацию:

```java
@Valid
public record Foo(String number,
                  Long code,
                  OffsetDateTime timestamp,
                  @Valid Bar bar) { }
```

В данном случае в сгенерированном валидаторе будет вызван `Validator` для типа `Bar`,
в случае если тип `Bar` также проаннотирован `@Valid` то `Validator<Bar>` будет сгенерирован для него,
в противном случае требуется предоставить самостоятельную реализацию в контейнер Bean'ов.

### Nullable vs NotNull

Предполагается что все поля по дефолту являются `NotNull`, значит для всех них будут сгенерированы `NotNull` проверки в `Validator`.

Чтобы указать поле как не обязательное, требуется пометить его любой `@Nullable` аннотацией,
для такого поля **не будет** сгенерирована проверка на *null*:

```java
@Valid
public record Foo(@Pattern("\\d+") String number,
                  @Range(from = 1, to = 10) Long code,
                  @Nullable OffsetDateTime timestamp,
                  Bar bar) { }
```

## Validate Record & Class

Предположим имеется Java Record:
```java
public record Foo(String number,
                  Long code,
                  OffsetDateTime timestamp,
                  Bar bar) { }

public record Bar(String number) {}
```

Для того чтобы для объекта был сгенерирован `Validator` и предоставлен фреймворком, требуется проаннотировать его `@Valid`:
```java
@Valid
public record Foo(String number,
                  Long code,
                  OffsetDateTime timestamp,
                  Bar bar) { }

@Valid
public record Bar(String number) {}
```

Для разметки валидации полей требуется использовать [соответствующие аннотации](#standard-validation).

Пример размеченных полей:
```java
@Valid
public record Foo(@Pattern("\\d+") String number,
                  @Range(from = 1, to = 10) Long code,
                  OffsetDateTime timestamp,
                  @Valid Bar bar) { }

@Valid
public record Bar(@Pattern("\\d+") String number) {}
```

Затем можно инжектить `Validator<Foo>` в существующие компоненты:
```java
@Component
public class Component {

    public Component(Validator<Foo> fooValidator) {

    }
}
```

### Record vs Class

Для Record классов используется синтаксис доступа к полям через Record-like контракты геттеров, в случае `Foo` и поля `code` будет использоваться *getter* `code()` в сгенерированном `Validator`

Для обычного класса ожидается что будет использоваться синтаксис Java *Getters*, пример для:

```java
@Valid
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

### Validator Options

Есть два вида валидации:
- Full - проверяются все поля которые только размечены, собираются все возможные ошибки валидации и только потом бросается ошибка. (Используется по дефолту)
- FailFast - ошибка бросается на первой встреченной ошибке валидации

Для FailFast валидации следует указать соответствующую опцию:
```java
ValidatorContext context = ValidationContext.builder().failFast(true).build();
List<Violation> violations = fooValidator.validate(value,context);
```

### Kotlin

Все контракты и примеры Java актуальны для Kotlin, отличие лишь в NotNull / Nullable, это определяется синтаксисом Kotlin и не требует валидации со стороны `Validator`

## Validate Method Arguments

Предположим имеется класс:
```java
@Component
public class Component {

    public int validate(int c1,
                        String c2,
                        ValidTaz c3) {
        return c1;
    }
}
```

Чтобы провалидировать его аргументы, требуется разметить их соответствующими [аннотациями](#validation):
```java
@Component
public class Component {

    @Validate
    public int validate(@Range(from = 1, to = 5) int c1,
                        @NotEmpty String c2,
                        @Valid ValidTaz c3) {
        return c1;
    }
}
```

### Options

Есть два вида валидации:
- Full - проверяются все поля которые только размечены, собираются все возможные ошибки валидации и только потом бросается ошибка. (Используется по дефолту)
- FailFast - ошибка бросается на первой встреченной ошибке валидации

Для FailFast валидации следует указать соответствующую опцию:
```java
@Component
public class Component {

    @Validate(failFast = true)
    public int validate(@Range(from = 1, to = 5) int c1,
                        @NotEmpty String c2,
                        @Valid ValidTaz c3) {
        return c1;
    }
}
```

## Validate Method Return Value

Предположим имеется класс:
```java
@Component
public class Component {

    public List<ValidTaz> validatedOutput(ValidTaz c3, ValidTaz c4) {
        return (c4 == null)
            ? List.of(c3)
            : List.of(c3, c4);
    }
}
```

Чтобы провалидировать его аргументы, требуется разметить их соответствующими [аннотациями](#validation):
```java
@Component
public class Component {

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    public List<ValidTaz> validatedOutput(ValidTaz c3, @Nullable ValidTaz c4) {
        return (c4 == null)
            ? List.of(c3)
            : List.of(c3, c4);
    }
}
```

### Options

Есть два вида валидации:
- Full - проверяются все поля которые только размечены, собираются все возможные ошибки валидации и только потом бросается ошибка. (Используется по дефолту)
- FailFast - ошибка бросается на первой встреченной ошибке валидации

Для FailFast валидации следует указать соответствующую опцию:
```java
@Component
public class Component {

    @Size(min = 1, max = 1)
    @Valid
    @Validate(failFast = true)
    public List<ValidTaz> validatedOutput(ValidTaz c3, @Nullable ValidTaz c4) {
        return (c4 == null)
            ? List.of(c3)
            : List.of(c3, c4);
    }
}
```

## Create Custom Annotation

Для создания кастомной аннотации требуется:

1) Создать реализацию `Validator`, который будет наследовать `Validator`:

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
public interface MyValidValidatorFactory extends ValidatorFactory<String> { }
```

3) Зарегистрировать Bean для `ValidatorFactory` для требуемых типов:

```java
@Module
public interface ValidationModule {
    default MyValidValidatorFactory myValidStringConstraintFactory() {
        return MyValidStringValidator::new;
    }
}
```

4) Создать аннотацию валидации и проаннотировать ее `@ValidatedBy` с ранее созданной `ValidatorFactory`:

```java
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(MyValidValidatorFactory.class)
public @interface MyValid { }
```

5) Проаннотировать поле/параметр/результат:

```java
@Valid
public record Foo(@MyValid String number) { }
```
