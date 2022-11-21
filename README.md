[[ Документация ]](https://tinkoff.github.io/kora/)

# Kora framework

kora это фреймворк нацеленный на оптимизацию используемых ресурсов путём уменьшения абстракций над "железом".
При этом kora стремится предоставить достаточно высокоуровневые декларативные API для разработчиков, которые на этапе компиляции преобразуются в оптимальный код для исполнения.

## Name origin

Kora (Kore) aka Persephone - ancient greek goddess of Spring

## Подключение

```groovy
    implementation platform("ru.tinkoff.kora:kora-parent:$koraVersion")
    annotationProcessor "ru.tinkoff.kora:annotation-processors:$koraVersion"
```
