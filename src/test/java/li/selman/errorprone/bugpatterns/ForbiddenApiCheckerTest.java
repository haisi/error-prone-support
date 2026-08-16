package li.selman.errorprone.bugpatterns;

import com.google.errorprone.CompilationTestHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests running the real checker through javac via {@link CompilationTestHelper}. Pure
 * parsing/matching-logic edge cases live in {@code ForbiddenSignatureParserTest} and {@code
 * ForbiddenApiMatcherTest} instead - this class focuses on symbol-resolution correctness (does the
 * checker actually see forbidden usages the way javac represents them?).
 */
final class ForbiddenApiCheckerTest {

    @TempDir
    Path tempDir;

    private CompilationTestHelper helper(String... signatureLines) throws IOException {
        Path file = tempDir.resolve("forbidden.txt");
        Files.write(file, java.util.List.of(signatureLines));
        return CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Signatures=" + file);
    }

    /** Pre-loaded with fixture classes under the (forbidden) com.foo.shaded package. */
    private CompilationTestHelper helperWithShadedPackageForbidden() throws IOException {
        return helper("com.foo.shaded.** @ Shaded dependencies are implementation details")
                .addSourceLines(
                        "com/foo/shaded/Bar.java",
                        "package com.foo.shaded;",
                        "public class Bar {",
                        "  public static int field;",
                        "  public void method() {}",
                        "}")
                .addSourceLines(
                        "com/foo/shaded/SomeInterface.java",
                        "package com.foo.shaded;",
                        "public interface SomeInterface {}")
                .addSourceLines(
                        "com/foo/shaded/Annotation.java",
                        "package com.foo.shaded;",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Target;",
                        "@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})",
                        "public @interface Annotation {}");
    }

    @Test
    void forbiddenExactClass() throws IOException {
        helper("java.util.Date")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: java.util.Date is forbidden.",
                        "  java.util.Date field;",
                        "}")
                .doTest();
    }

    @Test
    void allowedClassIsNotFlagged() throws IOException {
        helper("java.util.Date")
                .addSourceLines("Test.java", "class Test {", "  java.util.List<String> field;", "}")
                .doTest();
    }

    @Test
    void forbiddenExactClassWithCustomMessage() throws IOException {
        helper("java.util.Date @ Use java.time instead")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: java.util.Date is forbidden. Use java.time instead",
                        "  java.util.Date field;",
                        "}")
                .doTest();
    }

    @Test
    void forbiddenPackageMatchesNestedDescendant() throws IOException {
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "package test;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "  com.foo.shaded.Bar field;",
                        "}")
                .doTest();
    }

    @Test
    void directChildrenOnlyGlobDoesNotMatchNestedSubpackage() throws IOException {
        helper("com.foo.*")
                .addSourceLines("com/foo/bar/Baz.java", "package com.foo.bar;", "public class Baz {}")
                .addSourceLines("Test.java", "class Test {", "  com.foo.bar.Baz field;", "}")
                .doTest();
    }

    @Test
    void directChildrenOnlyGlobMatchesAClassDirectlyInThePackage() throws IOException {
        helper("com.foo.*")
                .addSourceLines("com/foo/Bar.java", "package com.foo;", "public class Bar {}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.* is forbidden",
                        "  com.foo.Bar field;",
                        "}")
                .doTest();
    }

    @Test
    void similarlyNamedSiblingPackageIsNotMatched() throws IOException {
        helper("com.foo.internal.**")
                .addSourceLines(
                        "com/foo/internalized/SomeClass.java",
                        "package com.foo.internalized;",
                        "public class SomeClass {}")
                .addSourceLines("Test.java", "class Test {", "  com.foo.internalized.SomeClass field;", "}")
                .doTest();
    }

    @Test
    void fullyQualifiedReferenceIsDetected() throws IOException {
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "  com.foo.shaded.Bar field;",
                        "}")
                .doTest();
    }

    @Test
    void importedReferenceIsDetected() throws IOException {
        // The import itself is also a usage site (see importOfAnUnusedForbiddenClassIsItselfFlagged),
        // so both it and the field declaration below are expected to be flagged independently.
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "import com.foo.shaded.Bar;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "  Bar field;",
                        "}")
                .doTest();
    }

    @Test
    void staticFieldAccessIsDetected() throws IOException {
        helper("java.lang.System#out @ Use the configured logger instead")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: java.lang.System#out is forbidden. Use the configured logger instead",
                        "    System.out.println(\"hi\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void instanceFieldAccessIsDetected() throws IOException {
        helper("com.foo.shaded.Bar#field")
                .addSourceLines(
                        "com/foo/shaded/Bar.java",
                        "package com.foo.shaded;",
                        "public class Bar {",
                        "  public static int field;",
                        "}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.Bar#field is forbidden",
                        "    int x = com.foo.shaded.Bar.field;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void staticMethodCallIsDetected() throws IOException {
        helper("java.lang.System#exit(int)")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: java.lang.System#exit(int) is forbidden",
                        "    System.exit(1);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void instanceMethodCallIsDetected() throws IOException {
        helper("com.foo.shaded.Bar#method()")
                .addSourceLines(
                        "com/foo/shaded/Bar.java",
                        "package com.foo.shaded;",
                        "public class Bar {",
                        "  public void method() {}",
                        "}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m(com.foo.shaded.Bar b) {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.Bar#method() is forbidden",
                        "    b.method();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void arrayParameterTypeIsMatched() throws IOException {
        helper("java.lang.String#valueOf(char[])")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: java.lang.String#valueOf(char[]) is forbidden",
                        "    String.valueOf(new char[0]);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void onlyTheForbiddenOverloadIsFlagged() throws IOException {
        helper("java.lang.Integer#valueOf(int)")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    Integer.valueOf(\"1\");",
                        "    // BUG: Diagnostic contains: java.lang.Integer#valueOf(int) is forbidden",
                        "    Integer.valueOf(1);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void constructorIsDetected() throws IOException {
        helper("java.lang.Integer#<init>(int)")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: java.lang.Integer#<init>(int) is forbidden",
                        "    Object o = new Integer(1);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void constructorBanDoesNotAlsoFlagUnrelatedClassUsage() throws IOException {
        // Regression guard: a constructor-specific ban must not make plain field-typed references
        // to the same class (no instantiation involved) light up too.
        helper("java.lang.Integer#<init>(int)")
                .addSourceLines("Test.java", "class Test {", "  Integer field;", "}")
                .doTest();
    }

    @Test
    void genericTypeArgumentIsDetected() throws IOException {
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "  List<com.foo.shaded.Bar> values;",
                        "}")
                .doTest();
    }

    @Test
    void annotationIsDetected() throws IOException {
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "@com.foo.shaded.Annotation",
                        "class Test {}")
                .doTest();
    }

    @Test
    void superclassIsDetected() throws IOException {
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "class Test extends com.foo.shaded.Bar {}")
                .doTest();
    }

    @Test
    void implementedInterfaceIsDetected() throws IOException {
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "abstract class Test implements com.foo.shaded.SomeInterface {}")
                .doTest();
    }

    @Test
    void thrownExceptionTypeIsDetected() throws IOException {
        helper("com.foo.shaded.MyException")
                .addSourceLines(
                        "com/foo/shaded/MyException.java",
                        "package com.foo.shaded;",
                        "public class MyException extends RuntimeException {}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.MyException is forbidden",
                        "  void m() throws com.foo.shaded.MyException {}",
                        "}")
                .doTest();
    }

    @Test
    void methodReferenceIsDetected() throws IOException {
        helper("java.lang.System#exit(int)")
                .addSourceLines(
                        "Test.java",
                        "import java.util.function.IntConsumer;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: java.lang.System#exit(int) is forbidden",
                        "  IntConsumer c = System::exit;",
                        "}")
                .doTest();
    }

    @Test
    void constructorReferenceIsDetected() throws IOException {
        helper("java.lang.Integer#<init>(int)")
                .addSourceLines(
                        "Test.java",
                        "import java.util.function.IntFunction;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: java.lang.Integer#<init>(int) is forbidden",
                        "  IntFunction<Integer> f = Integer::new;",
                        "}")
                .doTest();
    }

    @Test
    void importOfAnUnusedForbiddenClassIsItselfFlagged() throws IOException {
        // Documented behavior (see TASKS.md): imports are visited independently of any in-body
        // usage, so importing-but-never-using a forbidden class still produces one diagnostic, at
        // the import's own position.
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "import com.foo.shaded.Bar;",
                        "class Test {}")
                .doTest();
    }

    @Test
    void classLevelBanOnNewInstanceProducesExactlyOneDiagnostic() throws IOException {
        // Guards against double-reporting: `new Bar()` touches both an identifier/member-select
        // node (the type "Bar") and a NewClassTree - only the class-level signature is configured
        // here (no constructor-specific one), so exactly one diagnostic must be reported.
        helperWithShadedPackageForbidden()
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: com.foo.shaded.** is forbidden",
                        "  Object o = new com.foo.shaded.Bar();",
                        "}")
                .doTest();
    }

    @Test
    void jdkSystemOutBundleDetectsSystemOutUsage() throws IOException {
        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Bundles=jdk-system-out")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  void m() {",
                        "    // BUG: Diagnostic contains: java.lang.System#out is forbidden",
                        "    System.out.println(\"hi\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void jdkDefaultCharsetBundleDetectsImplicitDefaultCharsetConstructor() throws IOException {
        CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Bundles=jdk-default-charset")
                .addSourceLines(
                        "Test.java",
                        "import java.io.FileReader;",
                        "import java.io.IOException;",
                        "class Test {",
                        "  void m() throws IOException {",
                        "    // BUG: Diagnostic contains: java.io.FileReader#<init>(java.lang.String) is forbidden",
                        "    new FileReader(\"x\");",
                        "  }",
                        "}")
                .doTest();
    }
}
