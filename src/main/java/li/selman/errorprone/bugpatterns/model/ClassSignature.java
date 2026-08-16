package li.selman.errorprone.bugpatterns.model;

import java.util.Objects;
import java.util.Optional;

/** Forbids an exact class, e.g. {@code java.util.Date}. */
public record ClassSignature(String className, Optional<String> message) implements ForbiddenSignature {

    public ClassSignature {
        Objects.requireNonNull(className);
        Objects.requireNonNull(message);
    }

    @Override
    public String displayName() {
        return className;
    }
}
