package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.util.Size;
import io.koraframework.config.common.ConfigValue;

public final class SizeConfigValueMapper implements ConfigValueMapper<Size> {

@Nullable
@Override
public Size map(ConfigValue<?> value) {
if (value.isNull()) {
return null;
}

if (value instanceof ConfigValue.NumberValue number) {
return Size.ofBytesBinary(number.value().longValue());
} else if (value instanceof ConfigValue.StringValue string) {
return Size.parse(string.value());
} else {
throw ConfigValueException.unexpectedValueType(value, ConfigValue.StringValue.class);
}
}
}
