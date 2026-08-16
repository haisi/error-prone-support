package li.selman.errorprone.bugpatterns.qa2;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.errorprone.CompilationTestHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import li.selman.errorprone.bugpatterns.ForbiddenApiChecker;
import li.selman.errorprone.bugpatterns.matcher.ForbiddenApiMatcher;
import li.selman.errorprone.bugpatterns.matcher.UsageKey;
import li.selman.errorprone.bugpatterns.model.MethodSignature;
import li.selman.errorprone.bugpatterns.model.PackageSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-2 QA on the reverse-scan duplicate-signature precedence fix (see {@code
 * DuplicateSignaturePrecedenceQaTest} for the round-1, 2-signature version). This class pushes to
 * 3+ overlapping signatures, including across real {@code Bundles=} + multiple {@code Signatures=}
 * files through a live compile, not just direct {@link ForbiddenApiMatcher} unit tests.
 */
final class ThreeWayDuplicatePrecedenceQa2Test {

    @TempDir
    Path tempDir;

    @Test
    void lastOfThreeOverlappingPackageGlobsWinsAtUnitLevel() {
        // Three package signatures that all match com.foo.bar.Baz: an exact-package match, a
        // subpackage-including ancestor, and another exact-package match re-declared later. The
        // *last* one configured must win, regardless of which is textually/structurally "more
        // specific" - this matcher has no specificity ordering, only configuration order.
        PackageSignature exactFirst = new PackageSignature("com.foo.bar", false, Optional.of("first-exact"));
        PackageSignature ancestor = new PackageSignature("com.foo", true, Optional.of("second-ancestor"));
        PackageSignature exactLast = new PackageSignature("com.foo.bar", false, Optional.of("third-exact-again"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(exactFirst, ancestor, exactLast));

        assertThat(matcher.match(new UsageKey.TypeUsage("com.foo.bar.Baz"))).contains(exactLast);
    }

    @Test
    void lastOfThreeOverlappingMethodOverloadSignaturesWins() {
        MethodSignature first = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.of("a"));
        MethodSignature second = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.of("b"));
        MethodSignature third = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.of("c"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(first, second, third));

        assertThat(matcher.match(new UsageKey.MethodUsage("java.lang.System", "exit", List.of("int"))))
                .contains(third);
    }

    @Test
    void middleDuplicateDoesNotWinOverEitherNeighbor() {
        // Guards against an off-by-one in the reverse scan (e.g. accidentally starting at
        // size()-2, or stopping one short) by making the middle entry distinguishable from both
        // its neighbors - if the scan is even slightly wrong, this catches it whether it errs
        // toward "first wins" or "second-to-last wins".
        MethodSignature first = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.of("first"));
        MethodSignature middle = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.of("middle"));
        MethodSignature last = new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.of("last"));
        ForbiddenApiMatcher matcher = ForbiddenApiMatcher.of(List.of(first, middle, last));

        assertThat(matcher.match(new UsageKey.MethodUsage("java.lang.System", "exit", List.of("int"))))
                .contains(last);
    }

    // --- Live-compile version: Bundles= (loaded first) + two Signatures= files, both files -----
    // --- combined into ONE comma-separated Signatures= flag value (the only way ---------------
    // --- ForbiddenApiConfig.load actually merges multiple files - see the finding below about --
    // --- what happens if you instead pass two *separate* -XepOpt:...Signatures= occurrences). --

    @Test
    void bundleThenTwoCommaJoinedSignatureFilesEachRedeclaringTheSameConstructorRule_lastFileWins() throws IOException {
        // jdk-default-charset already bans java.io.FileReader#<init>(java.lang.String). Two more
        // Signatures= files, joined into a single comma-separated flag value, each re-declare it
        // with their own custom message; per ForbiddenApiConfig.load, bundles load first, then
        // Signatures= files in list order - the second file's message must win over both the
        // bundle's and the first file's.
        Path fileA = tempDir.resolve("a.txt");
        Files.write(fileA, List.of("java.io.FileReader#<init>(java.lang.String) @ message-from-file-a"));
        Path fileB = tempDir.resolve("b.txt");
        Files.write(fileB, List.of("java.io.FileReader#<init>(java.lang.String) @ message-from-file-b"));

        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs(
                        "-XepOpt:ForbiddenApi:Bundles=jdk-default-charset",
                        "-XepOpt:ForbiddenApi:Signatures=" + fileA + "," + fileB)
                .addSourceLines(
                        "Test.java",
                        "import java.io.FileReader;",
                        "import java.io.IOException;",
                        "class Test {",
                        "  void m() throws IOException {",
                        "    // BUG: Diagnostic contains: message-from-file-b",
                        "    new FileReader(\"x\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void bundleThenTwoCommaJoinedSignatureFilesEachRedeclaringTheSamePackageGlob_lastFileWins() throws IOException {
        Path fileA = tempDir.resolve("a.txt");
        Files.write(fileA, List.of("sun.** @ message-from-file-a"));
        Path fileB = tempDir.resolve("b.txt");
        Files.write(fileB, List.of("sun.** @ message-from-file-b"));

        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs(
                        "-XepOpt:ForbiddenApi:Bundles=jdk-internals",
                        "-XepOpt:ForbiddenApi:Signatures=" + fileA + "," + fileB)
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: message-from-file-b",
                        "  sun.misc.Unsafe field;",
                        "}")
                .doTest();
    }

    // --- Finding: repeating -XepOpt:ForbiddenApi:Signatures= as two SEPARATE flag occurrences --
    // --- (rather than one comma-joined value) does not merge them - ErrorProneFlags stores -----
    // --- flags in a plain HashMap<String,String> keyed by flag name, so the second occurrence --
    // --- completely overwrites the first in the flags map, and the first file's signatures ----
    // --- never load at all. Confirmed by javap against the real 2.50.0 jar (ErrorProneFlags --
    // --- $Builder.putFlag -> HashMap.put) before writing this test, not merely inferred. --------

    @Test
    void twoSeparateSignaturesFlagOccurrences_firstFileIsSilentlyDroppedNotMerged() throws IOException {
        Path fileA = tempDir.resolve("a.txt");
        Files.write(fileA, List.of("java.util.Date"));
        Path fileB = tempDir.resolve("b.txt");
        Files.write(fileB, List.of("java.io.FileReader#<init>(java.lang.String)"));

        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Signatures=" + fileA, "-XepOpt:ForbiddenApi:Signatures=" + fileB)
                .addSourceLines(
                        "Test.java",
                        "import java.io.FileReader;",
                        "import java.io.IOException;",
                        "class Test {",
                        // Not flagged: fileA's content was dropped when fileB's flag occurrence
                        // overwrote it in ErrorProneFlags' underlying map.
                        "  java.util.Date d;",
                        "  void m() throws IOException {",
                        "    // BUG: Diagnostic contains: java.io.FileReader#<init>(java.lang.String) is forbidden",
                        "    new FileReader(\"x\");",
                        "  }",
                        "}")
                .doTest();
    }
}
