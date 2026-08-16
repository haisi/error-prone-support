package li.selman.errorprone.bugpatterns.model;

import java.util.Objects;
import java.util.Optional;

/** Forbids a field or enum constant, e.g. {@code java.lang.System#out}. */
public record FieldSignature(String ownerClassName, String fieldName, Optional<String> message)
        implements ForbiddenSignature {

    public FieldSignature {
        Objects.requireNonNull(ownerClassName);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(message);
    }

    @Override
    public String displayName() {
        return ownerClassName + "#" + fieldName;
    }
}
