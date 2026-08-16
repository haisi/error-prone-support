package li.selman.errorprone.bugpatterns.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import li.selman.errorprone.bugpatterns.matcher.ForbiddenApiMatcher;
import li.selman.errorprone.bugpatterns.matcher.UsageKey;
import li.selman.errorprone.bugpatterns.model.ClassSignature;
import li.selman.errorprone.bugpatterns.model.ConstructorSignature;
import li.selman.errorprone.bugpatterns.model.MethodSignature;
import li.selman.errorprone.bugpatterns.model.PackageSignature;
import org.junit.jupiter.api.Test;

/**
 * {@link ForbiddenApiMatcher}'s class-level javadoc states: "the later one - in the order passed
 * to {@link ForbiddenApiMatcher#of(List)} - wins". {@code ForbiddenApiMatcherTest} verifies this
 * for {@link ClassSignature}/{@code FieldSignature} (map-keyed). This class is the analogous
 * regression guard for {@link PackageSignature}, {@link MethodSignature}, and {@link
 * ConstructorSignature} - stored in {@code List}s and matched by a reverse-order linear scan in
 * {@code matchType}/{@code matchOverload}, specifically so the later entry is found first.
 *
 * <p>An earlier version of {@code matchOverload}/the package scan iterated forward, making the
 * <em>earlier</em> signature win for these three kinds - contradicting the documented contract,
 * and meaning (via {@code ForbiddenApiConfig.load} appending bundle signatures before user {@code
 * Signatures=} file signatures) a user's own signature file could not override a built-in bundle's
 * package/method/constructor rule, unlike what it could already do for class/field rules. See
 * {@code ForbiddenApiCheckerQaTest#userSignatureFileOverridesABuiltinBundlesConstructorRuleMessage}
 * for the end-to-end version of this same fix.
 */
final class DuplicateSignaturePrecedenceQaTest {

    @Test
    void laterDuplicatePackageSignatureWinsOverEarlierOne() {
        PackageSignature first = new PackageSignature("com.foo", true, Optional.of("first"));
        PackageSignature second = new PackageSignature("com.foo", true, Optional.of("second"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(first, second));

        assertThat(matcher.match(new UsageKey.TypeUsage("com.foo.Bar"))).contains(second);
    }

    @Test
    void laterDuplicateMethodSignatureWinsOverEarlierOne() {
        MethodSignature first = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.of("first"));
        MethodSignature second = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.of("second"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(first, second));

        assertThat(matcher.match(new UsageKey.MethodUsage("java.lang.System", "exit", List.of("int"))))
                .contains(second);
    }

    @Test
    void laterDuplicateConstructorSignatureWinsOverEarlierOne() {
        ConstructorSignature first =
                new ConstructorSignature("java.lang.Integer", List.of("int"), Optional.of("first"));
        ConstructorSignature second =
                new ConstructorSignature("java.lang.Integer", List.of("int"), Optional.of("second"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(first, second));

        assertThat(matcher.match(new UsageKey.ConstructorUsage("java.lang.Integer", List.of("int"))))
                .contains(second);
    }
}
