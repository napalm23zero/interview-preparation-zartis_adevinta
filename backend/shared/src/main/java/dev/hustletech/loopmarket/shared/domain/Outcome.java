package dev.hustletech.loopmarket.shared.domain;

import java.util.function.Function;
import java.util.function.Supplier;

// Model expected absence explicitly so CRUD adapters return 404 without exceptions doing all the acting.
public sealed interface Outcome<T> permits Outcome.Success, Outcome.NotFound {
    <R> R fold(Function<? super T, ? extends R> success, Supplier<? extends R> notFound);

    record Success<T>(T value) implements Outcome<T> {
        @Override public <R> R fold(Function<? super T, ? extends R> success, Supplier<? extends R> notFound) {
            return success.apply(value);
        }
    }
    record NotFound<T>() implements Outcome<T> {
        @Override public <R> R fold(Function<? super T, ? extends R> success, Supplier<? extends R> notFound) {
            return notFound.get();
        }
    }
}
