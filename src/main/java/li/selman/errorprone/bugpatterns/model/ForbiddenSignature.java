package li.selman.errorprone.bugpatterns.model;

import java.util.Optional;

/**
 * A single forbidden-API rule parsed from a signature file, together with the optional custom
 * diagnostic message that follows it.
 */
public sealed interface ForbiddenSignature
        permits ClassSignature, PackageSignature, FieldSignature, MethodSignature, ConstructorSignature {

    /** The custom {@code @ message} configured for this rule, if any. */
    Optional<String> message();

    /** The signature exactly as it would appear in a signature file (without the message suffix). */
    String displayName();
}
