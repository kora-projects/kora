package ru.tinkoff.kora.test.redis;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RedisTestExtension.class)
@Inherited
public @interface RedisTestContainer {
}
