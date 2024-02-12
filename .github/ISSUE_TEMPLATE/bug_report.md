---
name: Сообщить об ошибке / Bug report
title: ''
labels: bug
---

**Kora version:**
- 1.0.0

**Java or Kotlin version:**
- Java 17

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

**Стек вызова или сгенерированный код или скриншоты / Stacktrace or generated code or screenshots:**

Stacktrace:
```text
Some of required json fields were not received: name(name)
```
