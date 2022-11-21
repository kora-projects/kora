package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;

import javax.annotation.Nullable;
import java.util.Optional;

@KoraApp
public interface AppWithOptionalComponents {
    default PresentInGraph presentInGraph() {
        return new PresentInGraph();
    }

    default NotEmptyOptionalParameter notEmptyOptionalParameter(Optional<PresentInGraph> param) {
        return new NotEmptyOptionalParameter(param.orElse(null));
    }

    default EmptyOptionalParameter emptyOptionalParameter(Optional<NotPresentInGraph> param) {
        return new EmptyOptionalParameter(param.orElse(null));
    }

    default NotEmptyValueOfOptional notEmptyValueOfOptional(ValueOf<Optional<PresentInGraph>> param) {
        return new NotEmptyValueOfOptional(param.get().orElse(null));
    }

    default EmptyValueOfOptional emptyValueOfOptional(ValueOf<Optional<NotPresentInGraph>> param) {
        return new EmptyValueOfOptional(param.get().orElse(null));
    }

    default NotEmptyNullable notEmptyNullable(@Nullable PresentInGraph param) {
        return new NotEmptyNullable(param);
    }

    default EmptyNullable emptyNullable(@Nullable NotPresentInGraph param) {
        return new EmptyNullable(param);
    }

    class NotPresentInGraph implements MockLifecycle {}

    class PresentInGraph implements MockLifecycle {}

    record NotEmptyOptionalParameter(@Nullable PresentInGraph value) implements MockLifecycle {}

    record EmptyOptionalParameter(@Nullable NotPresentInGraph value) implements MockLifecycle {}

    record NotEmptyValueOfOptional(@Nullable PresentInGraph value) implements MockLifecycle {}

    record EmptyValueOfOptional(@Nullable NotPresentInGraph value) implements MockLifecycle {}

    record NotEmptyNullable(@Nullable PresentInGraph value) implements MockLifecycle {}

    record EmptyNullable(@Nullable NotPresentInGraph value) implements MockLifecycle {}

}
