package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

import javax.annotation.Nullable;
import java.util.Optional;

@KoraApp
public interface AppWithOptionalComponents {
    default PresentInGraph presentInGraph() {
        return new PresentInGraph();
    }

    @Root
    default NotEmptyOptionalParameter notEmptyOptionalParameter(Optional<PresentInGraph> param) {
        return new NotEmptyOptionalParameter(param.orElse(null));
    }

    @Root
    default EmptyOptionalParameter emptyOptionalParameter(Optional<NotPresentInGraph> param) {
        return new EmptyOptionalParameter(param.orElse(null));
    }

    @Root
    default NotEmptyValueOfOptional notEmptyValueOfOptional(ValueOf<Optional<PresentInGraph>> param) {
        return new NotEmptyValueOfOptional(param.get().orElse(null));
    }

    @Root
    default EmptyValueOfOptional emptyValueOfOptional(ValueOf<Optional<NotPresentInGraph>> param) {
        return new EmptyValueOfOptional(param.get().orElse(null));
    }

    @Root
    default NotEmptyNullable notEmptyNullable(@Nullable PresentInGraph param) {
        return new NotEmptyNullable(param);
    }

    @Root
    default EmptyNullable emptyNullable(@Nullable NotPresentInGraph param) {
        return new EmptyNullable(param);
    }

    class NotPresentInGraph {}

    class PresentInGraph {}

    record NotEmptyOptionalParameter(@Nullable PresentInGraph value) {}

    record EmptyOptionalParameter(@Nullable NotPresentInGraph value) {}

    record NotEmptyValueOfOptional(@Nullable PresentInGraph value) {}

    record EmptyValueOfOptional(@Nullable NotPresentInGraph value) {}

    record NotEmptyNullable(@Nullable PresentInGraph value) {}

    record EmptyNullable(@Nullable NotPresentInGraph value) {}

}
