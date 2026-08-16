package li.selman.errorprone.bugpatterns.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Forbids a method overload, e.g. {@code java.lang.System#exit(int)}. */
public record MethodSignature(
        String ownerClassName, String methodName, List<String> parameterTypes, Optional<String> message)
        implements ForbiddenSignature {

    @SuppressWarnings("Var") // Normalizing a compact constructor's own parameter, not a field.
    public MethodSignature {
        Objects.requireNonNull(ownerClassName);
        Objects.requireNonNull(methodName);
        parameterTypes = List.copyOf(parameterTypes);
        Objects.requireNonNull(message);
    }

    @Override
    public String displayName() {
        return ownerClassName + "#" + methodName + "(" + String.join(", ", parameterTypes) + ")";
    }
}
