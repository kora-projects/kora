### Cassandra

Интеграция с Кассандрой работает по тому же принципу, что и [database](/features/database/#repository) и использует те же аннотации. Основное отличие заключается в конфигурации, пример ниже:

```
cassandra {
    auth {
        login = "user"
        password = "password"
    }
    basic {
        contactPoints = "localhost:9042,someotherhost:9042"
        dc = "datacenter1"
        sessionKeyspace = "test-db"
    }
    profiles {
        someProfile {
            basic.request.timeout = 10s
        }
    }
}
```

Для того, что бы применить настройки из профиля `someProfile`, указанного в конфигурации, достаточно сделать следующее:

```java
@CassandraProfile("someProfile")
@Query("SELECT id, value FROM test_table WHERE value = :value allow filtering")
TestEntity1 findOneByValue(String value);
```

Настройки, указанные в профиле, будут применяться к каждому запросу, конкретно в этом случае — будет установлен таймаут в 10с.

## Особенности Mapper'ов

При блокирующих вызовах в качестве мапперов для результата можно использовать любой из трёх: `CassandraResultSetMapper`, `CassandraRowSetMapper` и `CassandraRowMapper`. 
Из-за особенностей хелпера для извлечения данных из `AsyncResultSet` для асинхронных запросов(Mono или suspend), можно использовать только `CassandraRowMapper`

## CassandraQueryExecutorAccessor

Если annotation processor'у будет доступно несколько подходящих ExecutorAccessor, необходимо явно указать тип для каждого репозитория:
```java
@Repository()
public interface AllGeneratedRepository extends CassandraQueryExecutorAccessor {}
```

## Пример конфигурации

```
cassandra {
    # Авторизационные данные
    auth{
        login= "username"
        password = "password"
    }
    
    basic {
        # хосты нод кассандры
        contactPoints = [ "127.0.0.1:9042", "127.0.0.2:9042" ]
        # имя сессии
        sessionName = "some-session-name"
        
        # Настройки запросов
        request{
            # таймаут запроса
            
            timeout = 5s
            
            # уровень консистентности, допустимые значения:
            # ANY
            # ONE
            # TWO
            # THREE
            # QUORUM
            # ALL
            # LOCAL_QUORUM
            # EACH_QUORUM
            # SERIAL
            # LOCAL_SERIAL
            # LOCAL_ONE
            
            consistency = LOCAL_ONE
            
            # Ограничение размера страницы (определяет, сколько строк может быть возвращено за один запрос)
            
            pageSize = 5000
            
            # Уровень консистентности для легковесных транзакций(LWT). 
            # Допустимые значения SERIAL и LOCAL_SERIAL
            
            serialConsistency = LOCAL_SERIAL
            
            # Настройки значения идемпотентности для запросов
            
            defaultIdempotence = false
        }
        # Имя датацентра
        dc = "datacenter1"
        
        # Название keyspace для этой сессии
        sessionKeyspace = "test-db"
        
        # Флаг включения механизма избегания медленных реплик
        loadBalancingPolicy.slowReplicaAvoidance = true
        
        # Расположения бандла для подключения к Datastax Apache Cassandra.
        # Путь должен быть валидным URL'ом. По умолчанию, если не указан протокол, будет считаться что это file://
        cloud.secureConnectBundle = "/location/of/secure/connect/bundle"
    }
    # Расширенные настройки
    advanced {
        # Максимальное количество активных сессий
        sessionLeak.threshold = 4
        connection{
            # Таймаут подключения
            connectTimeout = 10s
            # Таймаут инициализации запроса
            initQueryTimeout = 10s
            # Таймаут установки keyspace
            setKeyspaceTimeout = 10s
            # Ограничение запросов на одно подключение
            maxRequestsPerConnection = 1024
            # Максимальное количество "осиротевших" запросов, т.е. тех, ответ на которые по тем или иным причинам прекратили ожидать. 
            maxOrphanRequests = 256
            # Выводить ошибки при инициализации в лог
            warnOnInitError = true
            
            # Настройки пула. 
            pool.localSize
            pool.remoteSize
        }
        # Повторять попытку инициализации, если при первой попытке все ноды, указанные в contactpoints, не ответили
        reconnectOnInit = false
        
        # Политика переподключения - базовая и максимальная задержка. 
        # По умолчанию, при неудачно попытке используется первое значение, затем при каждой следующей - удваивается, пока не достигнет максимального значения
        reconnectionPolicy{
            baseDelay = 1s
            maxDelay = 60s
        }
       
        sslEngineFactory{
            cipherSuites = [ "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA" ]
            # Валидация имени хоста
            hostnameValidation = true
            # Путь к хранилищу ключей
            keystorePath = "/path/to/client.keystore"
            # Пароль от хранилища ключей
            keystorePassword = "password"
            # Путь к доверенному хранилищу
            truststorePath = "/path/to/client.truststore"
            # Пароль от доверенного хранилища
            truststorePassword = "password"
        }
        
        # Генератор, добавляющий timestamp к каждому запросу. По умолчанию используется AtomicTimestampGenerator
        timestampGenerator{
            # Принудительно использовать Java system clock
            forceJavaClock = false
            # Указывает, насколько далеко в будущее могут "убегать" таймстэмпы при высокой нагрузке
            driftWarning.threshold = 1s
            # Интервал логирования предупреждений, есди таймстэмпы продолжают "убегать" вперёд.
            driftWarning.interval = 10s
        }
       
        protocol {
            # Версия протокола Cassandra
            version = "V4",
            # Сжатие
            compression = "lz4",
            # Максимальная длина фрейма в байтах
            maxFrameLength = 268435456
        }
        request {
            # Логировать предупреждение о том, что в запросе выполняется установка keyspace 
            warnIfSetKeyspace = true,
            # Настройки встроенного механизма трейсинга запросов
            trace {
                # Количество попыток 
                attempts = 5
                # Интервал между попытками
                interval = 1ms
                # Уровень консистентности
                consistency = ONE
            }
            logWarnings = true
        }
        # session-level метрики, по умолчанию выключены все
        metrics {
            # Список включенных метрик. Включаемые: bytes-sent, connected-nodes, cql-requests, 
            # cql-client-timeouts, cql-prepared-cache-size, throttling.delay, throttling.errors, continuous-cql-requests
            node.enabled = []
            session.enabled = []
            # Дополнительные настройки для метрик, если нужны:
            node.cqlMessages{
                lowestLatency = 1ms
                highestLatency = 1s
                significantDigits = 3
                refreshInterval= 5
            }
            session.cqlRequests {
                lowestLatency = 1ms
                highestLatency = 1s
                significantDigits = 3
                refreshInterval= 5
            }
            session.throttlingDelay{
                lowestLatency = 1ms
                highestLatency = 1s
                significantDigits = 3
                refreshInterval= 5
            }
        }
        socket {
            # Флаг для отключения Nagle алгоритма, по умолчанию true(выключен), т.к. драйвер имеет собственный message coalescing algorithm
            tcpNoDelay = true
            keepAlive = false
            # Позволять переиспользовать адрес
            reuseAddress = true
            lingerInterval = 0
            receiveBufferSize = 65535
            sendBufferSize = 65535
        }
        heartbeat {
            interval = 30s
            timeout = 2m
        }
        # Настройки, отвечающие за schema metadata
        metadata {
            schema{
                enabled = true
                requestTimeout = ${cassandra.basic.request.timeout}
                requestPageSize = ${cassandra.basic.request.request.pageSize}
                refreshedKeyspaces = [ "ks1", "ks2" ]
                # Время, которое драйвер ждёт перед применением обновления
                debouncer.window = 1s
                # Максимальное количество обновлений, которое может быть накоплено
                debouncer.maxEvents = 20
            }
            # Окно для отправки события.
            topologyEventDebouncer.window = 1s
            # Максимальное количество событий в пачке
            topologyEventDebouncer.maxEvents = 20
            tokenMapEnabled
        }
        controlConnection {
            timeout = 10s,
            schemaAgreement{
                interval = 200ms,
                timeout = 10s,
                warnOnFailure = true
            }
        }
        preparedStatements {
            # Выполнять подготовку запроса на всех нодах после её успешного выполнения на одной ноде.
            prepareOnAllNodes = true
            reprepareOnUp {
                # Подготавливать запросы для новых нод
                enabled = true
                # Проверять наличие prepare statement в system.prepared_statements ноды перед подготовкой
                checkSystemTable = false
                # Максимальной количество запросов, которые можно переподготовить
                maxStatements = 0
                # Максимальное количество конкурентных запросов
                maxParallelism = 100
                timeout = ${cassandra.advanced.connection.initQueryTimeout}
            }
            preparedCache.weakValues = false
        }
        # Настройки Netty event loop, используемой в драйвере
        netty{
            # Количество тредов
            ioGroup.size = 0
            # Настройки graceful shutdown
            ioGroup.shutdown {
                quietPeriod = 2
                timeout = 15
                unit = SECONDS
            }
            # Event loop группа, используемая только для админских задач, не связанных с IO
            adminGroup.size = 2
            adminGroup.shutdown {
                quietPeriod = 2
                timeout = 15
                unit = SECONDS
            }
            # Настройки того, как часто таймер должен пробуждаться для проверки просроченных задач
            timer.tickDuration = 100ms
            timer.ticksPerWheel = 2048
            daemon = false
        }
        coalescer.rescheduleInterval = 10 ms
        resolveContactPoints = false
    }
    profiles{
        # Настройки, переопределяемые в профиле
        slow {
            basic {
                # basic.request.timeout
                # basic.request.consistency
                # basic.request.pageSize
                # basic.request.serialConsistency
                # basic.request.defaultIdempotence
                # basic.loadBalancingPolicy.slowReplicaAvoidance
            }
            advanced {
                # advanced.loadBalancingPolicy.dcFailover.maxNodesPerRemoveDc
                # advanced.loadBalancingPolicy.dcFailover.allowForLocalConsistencyLevels
                # advanced.request.trace.consistency
                # advanced.request.trace.attempts
                # advanced.request.trace.interval
                # advanced.request.logWarnings
                # advanced.preparedStatements.prepareOnAllNodes
            }
        }
    }  
}

```
