package li.selman.errorprone.bugpatterns.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import li.selman.errorprone.bugpatterns.model.ClassSignature;
import li.selman.errorprone.bugpatterns.model.ConstructorSignature;
import li.selman.errorprone.bugpatterns.model.FieldSignature;
import li.selman.errorprone.bugpatterns.model.ForbiddenSignature;
import li.selman.errorprone.bugpatterns.model.MethodSignature;
import li.selman.errorprone.bugpatterns.model.PackageSignature;
import org.junit.jupiter.api.Test;

final class ForbiddenApiMatcherTest {

    @Test
    void matchesExactClass() {
        ClassSignature signature = new ClassSignature("java.util.Date", Optional.empty());
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(signature));

        assertThat(matcher.match(new UsageKey.TypeUsage("java.util.Date"))).contains(signature);
    }

    @Test
    void doesNotMatchUnrelatedClass() {
        ForbiddenApiMatcher matcher =
                ForbiddenApiMatcher.of(List.of(new ClassSignature("java.util.Date", Optional.empty())));

        assertThat(matcher.match(new UsageKey.TypeUsage("java.util.List"))).isEmpty();
    }

    @Test
    void classInDefaultPackageNeverMatchesAPackageSignature() {
        ForbiddenApiMatcher matcher =
                ForbiddenApiMatcher.of(List.of(new PackageSignature("com.foo", true, Optional.empty())));

        assertThat(matcher.match(new UsageKey.TypeUsage("TopLevelClass"))).isEmpty();
    }

    @Test
    void packageGlobWithSubpackagesMatchesThePackageItself() {
        PackageSignature signature = new PackageSignature("com.foo.shaded", true, Optional.empty());
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(signature));

        assertThat(matcher.match(new UsageKey.TypeUsage("com.foo.shaded.Bar"))).contains(signature);
    }

    @Test
    void packageGlobWithSubpackagesMatchesDeeplyNestedDescendants() {
        PackageSignature signature = new PackageSignature("com.foo.shaded", true, Optional.empty());
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(signature));

        assertThat(matcher.match(new UsageKey.TypeUsage("com.foo.shaded.deep.nested.Bar")))
                .contains(signature);
    }

    /**
     * The adversarial boundary case called out explicitly in the requirements: a package glob must
     * not match a sibling package whose name merely starts with the same characters.
     */
    @Test
    void packageGlobDoesNotMatchSimilarlyNamedSiblingPackage() {
        ForbiddenApiMatcher matcher =
                ForbiddenApiMatcher.of(List.of(new PackageSignature("com.foo.internal", true, Optional.empty())));

        assertThat(matcher.match(new UsageKey.TypeUsage("com.foo.internalized.SomeClass")))
                .isEmpty();
    }

    @Test
    void directChildrenOnlyGlobMatchesAClassDirectlyInThePackage() {
        PackageSignature signature = new PackageSignature("com.foo", false, Optional.empty());
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(signature));

        assertThat(matcher.match(new UsageKey.TypeUsage("com.foo.Bar"))).contains(signature);
    }

    @Test
    void directChildrenOnlyGlobDoesNotMatchANestedSubpackage() {
        ForbiddenApiMatcher matcher =
                ForbiddenApiMatcher.of(List.of(new PackageSignature("com.foo", false, Optional.empty())));

        assertThat(matcher.match(new UsageKey.TypeUsage("com.foo.bar.Baz"))).isEmpty();
    }

    @Test
    void exactClassSignatureTakesPrecedenceOverPackageSignature() {
        ClassSignature classSignature = new ClassSignature("com.foo.Bar", Optional.of("class-specific"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(
                List.of(new PackageSignature("com.foo", true, Optional.of("package-wide")), classSignature));

        assertThat(matcher.match(new UsageKey.TypeUsage("com.foo.Bar"))).contains(classSignature);
    }

    @Test
    void matchesField() {
        FieldSignature signature = new FieldSignature("java.lang.System", "out", Optional.empty());
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(signature));

        assertThat(matcher.match(new UsageKey.FieldUsage("java.lang.System", "out")))
                .contains(signature);
    }

    @Test
    void doesNotMatchDifferentFieldOnSameOwner() {
        ForbiddenApiMatcher matcher =
                ForbiddenApiMatcher.of(List.of(new FieldSignature("java.lang.System", "out", Optional.empty())));

        assertThat(matcher.match(new UsageKey.FieldUsage("java.lang.System", "err")))
                .isEmpty();
    }

    @Test
    void matchesMethodOverloadByExactParameterTypes() {
        MethodSignature exitInt = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.empty());
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(exitInt));

        assertThat(matcher.match(new UsageKey.MethodUsage("java.lang.System", "exit", List.of("int"))))
                .contains(exitInt);
    }

    @Test
    void doesNotMatchDifferentOverloadOfSameMethodName() {
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(
                new MethodSignature("java.lang.String", "format", List.of("java.lang.String"), Optional.empty())));

        assertThat(matcher.match(new UsageKey.MethodUsage(
                        "java.lang.String", "format", List.of("java.lang.String", "java.lang.Object[]"))))
                .isEmpty();
    }

    @Test
    void matchesConstructorOverloadByExactParameterTypes() {
        ConstructorSignature ctor = new ConstructorSignature("java.lang.Integer", List.of("int"), Optional.empty());
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(ctor));

        assertThat(matcher.match(new UsageKey.ConstructorUsage("java.lang.Integer", List.of("int"))))
                .contains(ctor);
    }

    @Test
    void doesNotMatchConstructorOnUnrelatedClass() {
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(
                List.of(new ConstructorSignature("java.lang.Integer", List.of("int"), Optional.empty())));

        assertThat(matcher.match(new UsageKey.ConstructorUsage("java.lang.Long", List.of("int"))))
                .isEmpty();
    }

    @Test
    void laterDuplicateClassSignatureWinsOverEarlierOne() {
        ClassSignature first = new ClassSignature("java.util.Date", Optional.of("first"));
        ClassSignature second = new ClassSignature("java.util.Date", Optional.of("second"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(first, second));

        assertThat(matcher.match(new UsageKey.TypeUsage("java.util.Date"))).contains(second);
    }

    @Test
    void laterDuplicateFieldSignatureWinsOverEarlierOne() {
        FieldSignature first = new FieldSignature("java.lang.System", "out", Optional.of("first"));
        FieldSignature second = new FieldSignature("java.lang.System", "out", Optional.of("second"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(first, second));

        assertThat(matcher.match(new UsageKey.FieldUsage("java.lang.System", "out")))
                .contains(second);
    }

    @Test
    void emptySignatureListMatchesNothing() {
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.<ForbiddenSignature>of());

        assertThat(matcher.match(new UsageKey.TypeUsage("java.util.Date"))).isEmpty();
        assertThat(matcher.match(new UsageKey.FieldUsage("java.lang.System", "out")))
                .isEmpty();
        assertThat(matcher.match(new UsageKey.MethodUsage("java.lang.System", "exit", List.of("int"))))
                .isEmpty();
        assertThat(matcher.match(new UsageKey.ConstructorUsage("java.lang.Integer", List.of("int"))))
                .isEmpty();
    }
}
