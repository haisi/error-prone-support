package li.selman.errorprone.bugpatterns.qa;

import com.google.errorprone.CompilationTestHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import li.selman.errorprone.bugpatterns.ForbiddenApiChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exploratory / adversarial checks against the real compiler, independent of {@code
 * ForbiddenApiCheckerTest}. Every method here is a standalone hypothesis about a possible gap; see
 * the QA report for which of these actually failed.
 */
final class ForbiddenApiCheckerQaTest {

    @TempDir
    Path tempDir;

    private CompilationTestHelper helper(String... signatureLines) throws IOException {
        Path file = tempDir.resolve("forbidden.txt");
        Files.write(file, java.util.List.of(signatureLines));
        return CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Signatures=" + file);
    }

    // --- 1. Overload / param-type matching edge cases ---------------------------------------

    @Test
    void genericMethodErasureIsUsedForOverloadMatching() throws IOException {
        // java.util.List<E>#add(E) erases to add(java.lang.Object).
        helper("java.util.List#add(java.lang.Object)")
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "class Test {",
                        "  void m(List<String> list) {",
                        "    // BUG: Diagnostic contains: java.util.List#add(java.lang.Object) is forbidden",
                        "    list.add(\"x\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void varargsCallSiteMatchesArrayParameterSignature() throws IOException {
        helper("java.lang.String#format(java.lang.String, java.lang.Object[])")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: java.lang.String#format(java.lang.String, java.lang.Object[]) is forbidden",
                        "    String.format(\"%s\", \"x\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void nestedClassAsParameterTypeUsesDottedQualifiedName() throws IOException {
        helper("Test#m(java.util.Map.Entry)")
                .addSourceLines(
                        "Test.java",
                        "import java.util.Map;",
                        "class Test {",
                        "  void m(Map.Entry<String, String> e) {}",
                        "  void caller(Map.Entry<String, String> e) {",
                        "    // BUG: Diagnostic contains: Test#m(java.util.Map.Entry) is forbidden",
                        "    m(e);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void methodBannedOnSuperclassIsCaughtWhenCalledThroughSubclassReference() throws IOException {
        helper("com.foo.Base#method()")
                .addSourceLines(
                        "com/foo/Base.java",
                        "package com.foo;",
                        "public class Base {",
                        "  public void method() {}",
                        "}")
                .addSourceLines("com/foo/Sub.java", "package com.foo;", "public class Sub extends Base {}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m(com.foo.Sub s) {",
                        "    // BUG: Diagnostic contains: com.foo.Base#method() is forbidden",
                        "    s.method();",
                        "  }",
                        "}")
                .doTest();
    }

    // --- 2. Package glob adversarial boundaries ----------------------------------------------

    @Test
    void topLevelPackageDoesNotMatchDifferentTopLevelPackageThatContainsItAsASubstring() throws IOException {
        // com.foo.** must not match com.foobar.* - "foobar" starts with "foo" but is not a
        // descendant package of "foo".
        helper("com.foo.**")
                .addSourceLines("com/foobar/Baz.java", "package com.foobar;", "public class Baz {}")
                .addSourceLines("Test.java", "class Test {", "  com.foobar.Baz field;", "}")
                .doTest();
    }

    @Test
    void classInDefaultPackageIsNeverCaughtByAPackageGlob() throws IOException {
        helper("com.foo.**")
                .addSourceLines("TopLevel.java", "public class TopLevel {}")
                .addSourceLines("Test.java", "class Test {", "  TopLevel field;", "}")
                .doTest();
    }

    // --- 3. Malformed configuration -----------------------------------------------------------

    @Test
    void tabsAndWhitespaceAroundSignatureAreTolerated() throws IOException {
        helper("\tjava.util.Date\t ")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: java.util.Date is forbidden.",
                        "  java.util.Date field;",
                        "}")
                .doTest();
    }

    @Test
    void windowsLineEndingsAreTolerated() throws IOException {
        Path file = tempDir.resolve("crlf.txt");
        Files.write(
                file, "java.util.Date\r\njava.lang.System#out\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Signatures=" + file)
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: java.util.Date is forbidden.",
                        "  java.util.Date field;",
                        "}")
                .doTest();
    }

    @Test
    void whitespaceOnlySignatureFileMatchesNothing() throws IOException {
        helper("   ", "\t", "")
                .addSourceLines("Test.java", "class Test {", "  java.util.Date field;", "}")
                .doTest();
    }

    // --- 5. Java language constructs --------------------------------------------------------

    @Test
    void recordComponentTypeIsDetected() throws IOException {
        helperWithShadedClass()
                .addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "record Test(com.foo.shaded.Bar bar) {}")
                .doTest();
    }

    @Test
    void varTypedLocalStillFlagsTheConstructorCall() throws IOException {
        helperWithShadedClass()
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "    var x = new com.foo.shaded.Bar();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void tryWithResourcesVariableTypeIsDetected() throws IOException {
        helper("com.foo.shaded.Bar")
                .addSourceLines(
                        "com/foo/shaded/Bar.java",
                        "package com.foo.shaded;",
                        "public class Bar implements AutoCloseable {",
                        "  public void close() {}",
                        "}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "    try (com.foo.shaded.Bar b = new com.foo.shaded.Bar()) {",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void anonymousClassUsageIsDetected() throws IOException {
        helper("com.foo.shaded.SomeInterface")
                .addSourceLines(
                        "com/foo/shaded/SomeInterface.java",
                        "package com.foo.shaded;",
                        "public interface SomeInterface {}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.SomeInterface is forbidden",
                        "    Object o = new com.foo.shaded.SomeInterface() {};",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void sealedPermitsClauseIsDetected() throws IOException {
        // A sealed type's permitted subclasses must be in the same package as the sealed type
        // itself when compiling outside the module system (as CompilationTestHelper does), so
        // both fixtures below have to share a package.
        helper("test.BarImpl")
                .addSourceLines("test/BarImpl.java", "package test;", "public final class BarImpl implements Shape {}")
                .addSourceLines(
                        "test/Shape.java",
                        "package test;",
                        "// BUG: Diagnostic contains: test.BarImpl is forbidden",
                        "public sealed interface Shape permits BarImpl {}")
                .doTest();
    }

    @Test
    void typeUseAnnotationOnAGenericArgumentIsDetected() throws IOException {
        helper("com.foo.shaded.Ann")
                .addSourceLines(
                        "com/foo/shaded/Ann.java",
                        "package com.foo.shaded;",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Target;",
                        "@Target(ElementType.TYPE_USE)",
                        "public @interface Ann {}")
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.Ann is forbidden",
                        "  List<@com.foo.shaded.Ann String> values;",
                        "}")
                .doTest();
    }

    @Test
    void enhancedForLoopVariableTypeIsDetected() throws IOException {
        helperWithShadedClass()
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "  void m(List<com.foo.shaded.Bar> list) {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "    for (com.foo.shaded.Bar b : list) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void localClassUsageIsDetected() throws IOException {
        helperWithShadedClass()
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "    class Local extends com.foo.shaded.Bar {}",
                        "  }",
                        "}")
                .doTest();
    }

    // --- 6. Suppression ----------------------------------------------------------------------

    @Test
    void suppressWarningsOnEnclosingMethodSuppressesTheDiagnostic() throws IOException {
        helper("java.util.Date")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  @SuppressWarnings(\"ForbiddenApi\")",
                        "  void m() {",
                        "    java.util.Date d = new java.util.Date();",
                        "  }",
                        "}")
                .doTest();
    }

    // --- 7. Duplicate diagnostics ------------------------------------------------------------

    @Test
    void classUsedAsFieldTypeAndSuperclassAndConstructorTargetProducesExactlyThreeDiagnostics() throws IOException {
        // Class-level ban only (no separate constructor-specific rule): field-type reference,
        // "extends" reference, and "new" reference are three independent usage sites and must
        // each be reported once - not zero, not deduplicated to fewer than three, not doubled.
        helper("com.foo.shaded.Bar")
                .addSourceLines("com/foo/shaded/Bar.java", "package com.foo.shaded;", "public class Bar {}")
                .addSourceLines(
                        "Impl.java",
                        "// BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "class Impl extends com.foo.shaded.Bar {}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "  com.foo.shaded.Bar field;",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "    Object o = new com.foo.shaded.Bar();",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helperWithShadedClass() throws IOException {
        return helper("com.foo.shaded.Bar")
                .addSourceLines("com/foo/shaded/Bar.java", "package com.foo.shaded;", "public class Bar {}");
    }

    // --- 8. Bundles= + Signatures= precedence for the same rule ------------------------------

    @Test
    void userSignatureFileOverridesABuiltinBundlesConstructorRuleMessage() throws IOException {
        // The jdk-default-charset bundle already bans java.io.FileReader#<init>(java.lang.String)
        // with its own built-in message. A user re-declaring the exact same constructor in their
        // own Signatures= file, to attach a project-specific message, now correctly overrides it:
        // ForbiddenApiConfig.load appends bundle signatures before file signatures, and
        // matchOverload scans candidates in reverse (last-configured first) precisely so the
        // later, user-supplied entry wins - see DuplicateSignaturePrecedenceQaTest.
        Path file = tempDir.resolve("override.txt");
        Files.write(file, java.util.List.of("java.io.FileReader#<init>(java.lang.String) @ project-specific-message"));
        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Bundles=jdk-default-charset", "-XepOpt:ForbiddenApi:Signatures=" + file)
                .addSourceLines(
                        "Test.java",
                        "import java.io.FileReader;",
                        "import java.io.IOException;",
                        "class Test {",
                        "  void m() throws IOException {",
                        "    // BUG: Diagnostic contains: project-specific-message",
                        "    new FileReader(\"x\");",
                        "  }",
                        "}")
                .doTest();
    }
}
