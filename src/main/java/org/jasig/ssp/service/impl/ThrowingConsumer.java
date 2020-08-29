package org.jasig.ssp.service.impl;

import java.util.Objects;
import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Exception, F extends Exception> {

    void accept(T t) throws E, F;

    static <T, E extends Exception ,F extends Exception> Consumer<T> unchecked(ThrowingConsumer<T, E, F> consumer) {
        Objects.requireNonNull(consumer);

        return consumer.uncheck();
    }

    default Consumer<T> uncheck() {
        return t -> {
            try {
                accept(t);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

}
