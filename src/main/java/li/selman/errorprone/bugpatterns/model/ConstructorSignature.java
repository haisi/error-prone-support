package li.selman.errorprone.bugpatterns.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Forbids a constructor overload, e.g. {@code java.lang.Integer#<init>(int)}. */
public record ConstructorSignature(String ownerClassName, List<String> parameterTypes, Optional<String> message)
        implements ForbiddenSignature {

    @SuppressWarnings("Var") // Normalizing a compact constructor's own parameter, not a field.
    public ConstructorSignature {
        Objects.requireNonNull(ownerClassName);
        parameterTypes = List.copyOf(parameterTypes);
        Objects.requireNonNull(message);
    }

    @Override
    public String displayName() {
        return ownerClassName + "#<init>(" + String.join(", ", parameterTypes) + ")";
    }
}
