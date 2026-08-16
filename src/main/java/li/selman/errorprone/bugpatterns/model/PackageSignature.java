package li.selman.errorprone.bugpatterns.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Forbids a package, e.g. {@code com.foo.**} (the package and all descendant packages) or {@code
 * com.foo.*} (only classes directly inside the package, not descendant packages).
 */
public record PackageSignature(String packageName, boolean includeSubpackages, Optional<String> message)
        implements ForbiddenSignature {

    public PackageSignature {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(message);
    }

    @Override
    public String displayName() {
        return packageName + (includeSubpackages ? ".**" : ".*");
    }
}
