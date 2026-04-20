package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.annotation.Cache

@Cache("dummy11")
interface DummyCacheInheritted : DummyCacheInherit
