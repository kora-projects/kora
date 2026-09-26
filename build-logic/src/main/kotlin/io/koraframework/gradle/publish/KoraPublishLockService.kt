package io.koraframework.gradle.publish

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class KoraPublishLockService : BuildService<BuildServiceParameters.None>
