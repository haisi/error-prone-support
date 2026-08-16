package li.selman.errorprone.bugpatterns.matcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import li.selman.errorprone.bugpatterns.model.ClassSignature;
import li.selman.errorprone.bugpatterns.model.ConstructorSignature;
import li.selman.errorprone.bugpatterns.model.FieldSignature;
import li.selman.errorprone.bugpatterns.model.ForbiddenSignature;
import li.selman.errorprone.bugpatterns.model.MethodSignature;
import li.selman.errorprone.bugpatterns.model.PackageSignature;

/**
 * Answers "is this usage forbidden?" for a set of {@link ForbiddenSignature}s, purely in terms of
 * {@link UsageKey} - no javac or Error Prone types involved, so this class is unit-testable
 * without a compiler.
 *
 * <p>Signatures are indexed once at construction time for fast lookup. When multiple signatures
 * would produce the same index key (e.g. the same class banned twice), the later one - in the
 * order passed to {@link #of(List)} - wins; see the README's note on duplicate signatures.
 */
public final class ForbiddenApiMatcher {

    private final Map<String, ClassSignature> classesByName;
    private final List<PackageSignature> packages;
    private final Map<String, FieldSignature> fieldsByKey;
    private final Map<String, List<MethodSignature>> methodsByKey;
    private final Map<String, List<ConstructorSignature>> constructorsByKey;

    private ForbiddenApiMatcher(
            Map<String, ClassSignature> classesByName,
            List<PackageSignature> packages,
            Map<String, FieldSignature> fieldsByKey,
            Map<String, List<MethodSignature>> methodsByKey,
            Map<String, List<ConstructorSignature>> constructorsByKey) {
        this.classesByName = classesByName;
        this.packages = packages;
        this.fieldsByKey = fieldsByKey;
        this.methodsByKey = methodsByKey;
        this.constructorsByKey = constructorsByKey;
    }

    public static ForbiddenApiMatcher of(List<ForbiddenSignature> signatures) {
        Map<String, ClassSignature> classesByName = new HashMap<>();
        List<PackageSignature> packages = new ArrayList<>();
        Map<String, FieldSignature> fieldsByKey = new HashMap<>();
        Map<String, List<MethodSignature>> methodsByKey = new HashMap<>();
        Map<String, List<ConstructorSignature>> constructorsByKey = new HashMap<>();

        for (ForbiddenSignature signature : signatures) {
            switch (signature) {
                case ClassSignature s -> classesByName.put(s.className(), s);
                case PackageSignature s -> packages.add(s);
                case FieldSignature s -> fieldsByKey.put(fieldKey(s.ownerClassName(), s.fieldName()), s);
                case MethodSignature s ->
                    methodsByKey
                            .computeIfAbsent(methodKey(s.ownerClassName(), s.methodName()), k -> new ArrayList<>())
                            .add(s);
                case ConstructorSignature s ->
                    constructorsByKey
                            .computeIfAbsent(s.ownerClassName(), k -> new ArrayList<>())
                            .add(s);
            }
        }
        return new ForbiddenApiMatcher(
                Map.copyOf(classesByName),
                List.copyOf(packages),
                Map.copyOf(fieldsByKey),
                immutableCopyOfValues(methodsByKey),
                immutableCopyOfValues(constructorsByKey));
    }

    private static <S> Map<String, List<S>> immutableCopyOfValues(Map<String, List<S>> map) {
        Map<String, List<S>> copy = new HashMap<>();
        map.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    public Optional<ForbiddenSignature> match(UsageKey usage) {
        return switch (usage) {
            case UsageKey.TypeUsage u -> matchType(u.className());
            case UsageKey.FieldUsage u ->
                Optional.ofNullable(fieldsByKey.get(fieldKey(u.ownerClassName(), u.fieldName())));
            case UsageKey.MethodUsage u ->
                matchOverload(
                        methodsByKey.getOrDefault(methodKey(u.ownerClassName(), u.methodName()), List.of()),
                        u.parameterTypes());
            case UsageKey.ConstructorUsage u ->
                matchOverload(constructorsByKey.getOrDefault(u.ownerClassName(), List.of()), u.parameterTypes());
        };
    }

    private Optional<ForbiddenSignature> matchType(String className) {
        ClassSignature exact = classesByName.get(className);
        if (exact != null) {
            return Optional.of(exact);
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            // Class in the unnamed/default package: it has no package to match against.
            return Optional.empty();
        }
        String packageName = className.substring(0, lastDot);
        // Reverse order: a later-configured package signature must win over an earlier one that
        // also matches, consistent with the "last one wins" contract documented on this class and
        // honored by the map-backed class/field lookups above via plain HashMap#put overwrite.
        for (int i = packages.size() - 1; i >= 0; i--) {
            PackageSignature candidate = packages.get(i);
            if (matchesPackage(candidate, packageName)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Package-visible for direct unit testing of the glob boundary semantics. */
    static boolean matchesPackage(PackageSignature signature, String packageName) {
        if (packageName.equals(signature.packageName())) {
            return true;
        }
        return signature.includeSubpackages() && packageName.startsWith(signature.packageName() + ".");
    }

    private static <S extends ForbiddenSignature> Optional<ForbiddenSignature> matchOverload(
            List<S> candidates, List<String> parameterTypes) {
        // Reverse order: same "last one wins" reasoning as matchType's package scan above.
        for (int i = candidates.size() - 1; i >= 0; i--) {
            S candidate = candidates.get(i);
            List<String> candidateParameterTypes = candidate instanceof MethodSignature method
                    ? method.parameterTypes()
                    : ((ConstructorSignature) candidate).parameterTypes();
            if (candidateParameterTypes.equals(parameterTypes)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static String fieldKey(String ownerClassName, String fieldName) {
        return ownerClassName + "#" + fieldName;
    }

    private static String methodKey(String ownerClassName, String methodName) {
        return ownerClassName + "#" + methodName;
    }
}
