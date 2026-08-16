package li.selman.errorprone.bugpatterns.qa2;

import com.google.errorprone.CompilationTestHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import li.selman.errorprone.bugpatterns.ForbiddenApiChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-2 QA on the constructor-injection -> lazy-{@link com.google.errorprone.VisitorState}-read
 * fix for {@code ForbiddenApiChecker}'s configuration (see TASKS.md's "Consumer-example validation
 * finding" for the original {@code ServiceConfigurationError} this fixed). {@code
 * CompilationTestHelper} can't reproduce the original bug at all (it uses Error Prone's own
 * reflective injector, not {@code java.util.ServiceLoader}) - the real end-to-end confirmation of
 * that fix is a separate, standalone {@code mvn compile} of {@code examples/consumer-example}
 * against the locally-installed jar (done independently as part of this QA pass, not via a test
 * here). What CAN be exercised here, in-process, is: (1) the checker behaves sensibly with zero
 * {@code -XepOpt:ForbiddenApi:*} flags at all, and (2) the per-instance memoized {@code matcher}
 * field is loaded exactly once and then reused correctly across every file in a single compilation
 * unit (multiple {@code addSourceLines} sources sharing one {@code CompilationTestHelper} run
 * exercise the same checker instance across several trees, the same way multiple files in a single
 * real {@code javac}/Maven-compile invocation would).
 */
final class LazyConfigLoadingQa2Test {

    @TempDir
    Path tempDir;

    @Test
    void noXepOptFlagsAtAll_checkerEnabledOnlyViaXepDoesNotCrashAndFlagsNothing() {
        // Enabling the checker via -Xep:ForbiddenApi:ERROR alone (CompilationTestHelper does this
        // automatically for the class under test) with zero -XepOpt:ForbiddenApi:* flags: this
        // exercises state.errorProneOptions().getFlags() actually being empty/absent for both
        // keys, not merely a code path we assume is fine.
        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  java.util.Date d = new java.util.Date();",
                        "  void m() {",
                        "    System.out.println(\"hi\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void memoizedMatcherIsAppliedConsistentlyAcrossMultipleFilesInOneCompilationUnit() throws IOException {
        // Single CompilationTestHelper#doTest() call = single "compilation", analogous to a
        // single javac/Maven-compile invocation processing several source files together with one
        // shared set of -XepOpt flags. The checker's matcher field is populated by the FIRST tree
        // visited (order is unspecified) and then reused for every subsequent tree across every
        // file below - if memoization somehow captured a wrong/partial value, only some files
        // would be flagged correctly.
        Path file = tempDir.resolve("forbidden.txt");
        Files.write(file, List.of("java.util.Date", "java.lang.System#out"));

        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Signatures=" + file)
                .addSourceLines(
                        "A.java",
                        "class A {",
                        "  // BUG: Diagnostic contains: java.util.Date is forbidden",
                        "  java.util.Date d;",
                        "}")
                .addSourceLines(
                        "B.java",
                        "class B {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: java.lang.System#out is forbidden",
                        "    System.out.println(\"x\");",
                        "  }",
                        "}")
                .addSourceLines(
                        "C.java",
                        "class C {",
                        "  // BUG: Diagnostic contains: java.util.Date is forbidden",
                        "  java.util.Date d2;",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: java.lang.System#out is forbidden",
                        "    System.out.println(\"y\");",
                        "  }",
                        "}")
                .doTest();
    }
}
