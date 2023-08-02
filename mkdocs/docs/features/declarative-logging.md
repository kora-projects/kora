# Декларативное логирование

Модуль позволяет удобно логировать вход в метод и выход из него, а также входные/выходной параметры.

Для подключения декларативного логирования необходимо добавить следующую зависимость

1) Аннотации
```groovy
implemenation 'ru.tinkoff.kora:declarative-logging-annotation'
```

2) Также необходим annotation-processor (для java) или symbol-processor (для kotlin).

Java:
```groovy
annotationProcessor 'ru.tinkoff.kora:declarative-logging-annotation-processor'
```
или же можно подключить общий процессор:
```groovy
annotationProcessor 'ru.tinkoff.kora:annotation-processors'
```

Kotlin:
```groovy
ksp 'ru.tinkoff.kora:declarative-logging-annotation-processor'
```
или же можно подключить общий процессор
```groovy
ksp 'ru.tinkoff.kora:symbol-processors'
```

# Примеры

```java 
@Log
public String methodWithArgs(String strParam, int numParam) {
    return "testResult";
}
```

<table>
    <thead>
        <th>Уровень логгирования</th>
        <th>Лог</th>
    </thead>
    <tr>
        <td>TRACE, DEBUG</td>
        <td>
            <p>DEBUG [] r.t.e.e.Example.methodWithArgs: > {data: {strParam: "s", numParam: "4"}}</p>
            <p>DEBUG [] r.t.e.e.Example.methodWithArgs: < {data: {out: "testResult"}}</p>
        </td>
    </tr>
    <tr>
        <td>INFO</td>
        <td>
            <p>INFO [] r.t.e.e.Example.methodWithArgs: ></p>
            <p>INFO [] r.t.e.e.Example.methodWithArgs: <</p>
        </td>
    </tr>
</table>

---

```java 
@Log.in
public String methodWithReturnAndOnlyLogArgs(@Log.off String strParam,int numParam){
    return"testResult";
}
```

<table>
    <thead>
        <th>Уровень логгирования</th>
        <th>Лог</th>
    </thead>
    <tr>
        <td>TRACE, DEBUG</td>
        <td>
            <p>DEBUG [] r.t.e.e.Example.methodWithArgs: > {data: {numParam: "4"}}</p>
        </td>
    </tr>
    <tr>
        <td>INFO</td>
        <td>
            <p>INFO [] r.t.e.e.Example.methodWithArgs: ></p>
        </td>
    </tr>
</table>

---

```java 
@Log.out
public String methodWithOnlyLogReturnAndArgs(String strParam, int numParam) {
    return "testResult";
}
```

<table>
    <thead>
        <th>Уровень логгирования</th>
        <th>Лог</th>
    </thead>
    <tr>
        <td>TRACE, DEBUG</td>
        <td>
            <p>DEBUG [] r.t.e.e.Example.methodWithArgs: < {data: {out: "testResult"}}</p>
        </td>
    </tr>
    <tr>
        <td>INFO</td>
        <td>
            <p>INFO [] r.t.e.e.Example.methodWithArgs: <</p>
        </td>
    </tr>
</table>

---

```java 
@Log.out
@Log.off
public String methodWithOnlyLogReturnAndArgs(String strParam,int numParam){
    return"testResult";
    }
```

<table>
    <thead>
        <th>Уровень логгирования</th>
        <th>Лог</th>
    </thead>
    <tr>
        <td>TRACE, DEBUG</td>
        <td>
            <p>INFO [] r.t.e.e.Example.methodWithArgs: <</p>
        </td>
    </tr>
    <tr>
        <td>INFO</td>
        <td>
            <p>INFO [] r.t.e.e.Example.methodWithArgs: <</p>
        </td>
    </tr>
</table>
