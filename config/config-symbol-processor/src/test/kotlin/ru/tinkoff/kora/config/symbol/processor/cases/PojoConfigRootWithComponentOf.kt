package ru.tinkoff.kora.config.symbol.processor.cases

import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.config.common.ConfigRoot

@ConfigRoot(AppWithConfigWithModule::class)
class PojoConfigRootWithComponentOf(
    @Tag(ClassConfig::class) val pojo: ClassConfig,
    @Tag(DataClassConfig::class) val rec: DataClassConfig
)
