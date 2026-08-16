package li.selman.errorprone.bugpatterns.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.errorprone.ErrorProneFlags;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import li.selman.errorprone.bugpatterns.matcher.ForbiddenApiMatcher;
import li.selman.errorprone.bugpatterns.matcher.UsageKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ForbiddenApiConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSignaturesFromAFile() throws IOException {
        Path file = tempDir.resolve("forbidden.txt");
        Files.writeString(file, "java.util.Date\n");

        ForbiddenApiMatcher matcher =
                ForbiddenApiConfig.load(ErrorProneFlags.fromMap(Map.of("ForbiddenApi:Signatures", file.toString())));

        assertThat(matcher.match(new UsageKey.TypeUsage("java.util.Date"))).isPresent();
    }

    @Test
    void loadsMultipleCommaSeparatedSignatureFiles() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "java.util.Date\n");
        Files.writeString(file2, "java.lang.System#out\n");

        ForbiddenApiMatcher matcher = ForbiddenApiConfig.load(
                ErrorProneFlags.fromMap(Map.of("ForbiddenApi:Signatures", file1 + "," + file2)));

        assertThat(matcher.match(new UsageKey.TypeUsage("java.util.Date"))).isPresent();
        assertThat(matcher.match(new UsageKey.FieldUsage("java.lang.System", "out")))
                .isPresent();
    }

    @Test
    void loadsABuiltinBundle() {
        ForbiddenApiMatcher matcher =
                ForbiddenApiConfig.load(ErrorProneFlags.fromMap(Map.of("ForbiddenApi:Bundles", "jdk-system-out")));

        assertThat(matcher.match(new UsageKey.FieldUsage("java.lang.System", "out")))
                .isPresent();
        assertThat(matcher.match(new UsageKey.FieldUsage("java.lang.System", "err")))
                .isPresent();
    }

    @Test
    void combinesBundlesAndSignatureFiles() throws IOException {
        Path file = tempDir.resolve("forbidden.txt");
        Files.writeString(file, "java.util.Date\n");

        ForbiddenApiMatcher matcher = ForbiddenApiConfig.load(ErrorProneFlags.fromMap(
                Map.of("ForbiddenApi:Bundles", "jdk-system-out", "ForbiddenApi:Signatures", file.toString())));

        assertThat(matcher.match(new UsageKey.TypeUsage("java.util.Date"))).isPresent();
        assertThat(matcher.match(new UsageKey.FieldUsage("java.lang.System", "out")))
                .isPresent();
    }

    @Test
    void noConfigurationMatchesNothing() {
        ForbiddenApiMatcher matcher = ForbiddenApiConfig.load(ErrorProneFlags.empty());

        assertThat(matcher.match(new UsageKey.TypeUsage("java.util.Date"))).isEmpty();
    }

    @Test
    void unknownBundleNameFailsWithAHelpfulMessage() {
        assertThatThrownBy(() -> ForbiddenApiConfig.load(
                        ErrorProneFlags.fromMap(Map.of("ForbiddenApi:Bundles", "no-such-bundle"))))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("no-such-bundle")
                .hasMessageContaining(BuiltinBundles.NAMES.toString());
    }

    @Test
    void missingSignatureFileFailsWithAnIOException() {
        String missingPath = tempDir.resolve("does-not-exist.txt").toString();

        assertThatThrownBy(() -> ForbiddenApiConfig.load(
                        ErrorProneFlags.fromMap(Map.of("ForbiddenApi:Signatures", missingPath))))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void malformedSignatureFileFailsWithAParseException() throws IOException {
        Path file = tempDir.resolve("bad.txt");
        Files.writeString(file, "1invalid.Name\n");

        assertThatThrownBy(() -> ForbiddenApiConfig.load(
                        ErrorProneFlags.fromMap(Map.of("ForbiddenApi:Signatures", file.toString()))))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("%s:1", file);
    }

    @Test
    void loadsTheNonJspecifyNullableBundle() {
        ForbiddenApiMatcher matcher = ForbiddenApiConfig.load(
                ErrorProneFlags.fromMap(Map.of("ForbiddenApi:Bundles", "non-jspecify-nullable")));

        assertThat(matcher.match(new UsageKey.TypeUsage("javax.annotation.Nullable")))
                .isPresent();
        assertThat(matcher.match(new UsageKey.TypeUsage("org.springframework.lang.NonNullApi")))
                .isPresent();
        assertThat(matcher.match(new UsageKey.TypeUsage("io.micrometer.core.lang.Nullable")))
                .isPresent();
        assertThat(matcher.match(new UsageKey.TypeUsage("org.jspecify.annotations.Nullable")))
                .isEmpty();
    }

    @Test
    void allBuiltinBundlesLoadWithoutError() {
        for (String bundleName : BuiltinBundles.NAMES) {
            ForbiddenApiMatcher matcher =
                    ForbiddenApiConfig.load(ErrorProneFlags.fromMap(Map.of("ForbiddenApi:Bundles", bundleName)));
            assertThat(matcher).isNotNull();
        }
    }

    @Test
    void allBundleNamesAreKnown() {
        assertThat(List.of("jdk-system-out", "jdk-default-charset", "jdk-internals", "non-jspecify-nullable"))
                .containsExactlyInAnyOrderElementsOf(BuiltinBundles.NAMES);
    }
}
