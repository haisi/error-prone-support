package li.selman.errorprone.bugpatterns.matcher;

import java.util.List;

/**
 * A plain, javac/Error-Prone-free description of a single resolved usage site, as translated from
 * a javac {@code Symbol} by the checker's integration layer. Keeping this free of javac types is
 * what lets {@link ForbiddenApiMatcher} be unit-tested without compiling anything.
 */
public sealed interface UsageKey {

    /** A reference to a class/interface/enum/record/annotation type, by fully qualified name. */
    record TypeUsage(String className) implements UsageKey {}

    /** A reference to a field or enum constant. */
    record FieldUsage(String ownerClassName, String fieldName) implements UsageKey {}

    /** A method invocation or method reference. */
    record MethodUsage(String ownerClassName, String methodName, List<String> parameterTypes) implements UsageKey {
        @SuppressWarnings("Var") // Normalizing a compact constructor's own parameter, not a field.
        public MethodUsage {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    /** A constructor invocation or constructor reference ({@code Foo::new}). */
    record ConstructorUsage(String ownerClassName, List<String> parameterTypes) implements UsageKey {
        @SuppressWarnings("Var") // Normalizing a compact constructor's own parameter, not a field.
        public ConstructorUsage {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }
}
