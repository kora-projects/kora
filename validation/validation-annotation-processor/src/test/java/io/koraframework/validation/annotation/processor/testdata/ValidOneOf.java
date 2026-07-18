package io.koraframework.validation.annotation.processor.testdata;

import io.koraframework.validation.common.annotation.OneOf;
import io.koraframework.validation.common.annotation.Valid;

@Valid
public record ValidOneOf(@OneOf({"NEW", "DONE"}) String status) {

}
