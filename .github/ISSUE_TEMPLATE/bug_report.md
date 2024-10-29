---
name: Сообщить об ошибке / Bug report
title: ''
about: Сообщить об ошибке и помочь улучшить Kora / Create a report to help us improve Kora
labels: bug
---

**Kora version:**
- 1.0.0

**Java or Kotlin and KSP version:**
- Java 17

**Lombok usage:**
- Yes/No

**Описание проблемы / Describe the bug:**

`@Nullable` annotation doesn't mark field as optional for `@Json` reader

```java
@Json
public record User(@Nullable String name) {}
```

**Текущее поведение / Current behavior:**

`@Nullable` annotation is ignored

**Ожидаемое поведение / Expected behavior:**

`@Nullable` annotation must mark field as optional

**Стек вызова или сгенерированный код или скриншоты / Stacktrace or generated code or screenshots (*Optional*):**

Stacktrace:
```text
Some of required json fields were not received: name(name)
```
