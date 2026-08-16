package li.selman.errorprone.bugpatterns.qa2;

import com.google.errorprone.CompilationTestHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import li.selman.errorprone.bugpatterns.ForbiddenApiChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Independent, second-round QA pass on {@code ForbiddenApiChecker}'s {@code void.class} NPE fix
 * (see {@code ForbiddenApiCheckerTest#voidClassLiteralDoesNotCrashTheChecker} for the original
 * regression test). Two goals:
 *
 * <ol>
 *   <li>Confirm the fix generalizes to every primitive/array class-literal shape, not just {@code
 *       void.class} specifically.
 *   <li>Actively try to prove wrong the theory (documented on {@code matchMethodInvocation}) that
 *       {@code matchMethodInvocation}/{@code matchNewClass}/{@code matchMemberReference} can never
 *       observe a null-owner symbol, by exercising every construct the QA brief called out:
 *       bridge-adjacent covariant overrides, record compact constructors and generated accessors,
 *       enum {@code values()}/{@code valueOf()}, annotation-interface {@code value()} calls, {@code
 *       int[]::new} as a constructor-reference target, lambda bodies, and generic method references
 *       with inferred type arguments.
 * </ol>
 */
final class NullOwnerAndClassLiteralQa2Test {

    @TempDir
    Path tempDir;

    private CompilationTestHelper helper(String... signatureLines) throws IOException {
        Path file = tempDir.resolve("forbidden.txt");
        Files.write(file, java.util.List.of(signatureLines));
        return CompilationTestHelper.newInstance(ForbiddenApiChecker.class, getClass())
                .setArgs("-XepOpt:ForbiddenApi:Signatures=" + file);
    }

    // --- 1. Other primitive/array class-literal shapes, zero signatures configured -----------

    @Test
    void intDotClassLiteralDoesNotCrashTheChecker() throws IOException {
        helper("java.util.Date")
                .addSourceLines("Test.java", "class Test {", "  Class<?> c = int.class;", "}")
                .doTest();
    }

    @Test
    void booleanDotClassLiteralDoesNotCrashTheChecker() throws IOException {
        helper("java.util.Date")
                .addSourceLines("Test.java", "class Test {", "  Class<?> c = boolean.class;", "}")
                .doTest();
    }

    @Test
    void primitiveArrayClassLiteralDoesNotCrashTheChecker() throws IOException {
        helper("java.util.Date")
                .addSourceLines("Test.java", "class Test {", "  Class<?> c = int[].class;", "}")
                .doTest();
    }

    @Test
    void twoDimensionalPrimitiveArrayClassLiteralDoesNotCrashTheChecker() throws IOException {
        helper("java.util.Date")
                .addSourceLines("Test.java", "class Test {", "  Class<?> c = int[][].class;", "}")
                .doTest();
    }

    @Test
    void referenceArrayClassLiteralDoesNotCrashTheChecker() throws IOException {
        helper("java.util.Date")
                .addSourceLines("Test.java", "class Test {", "  Class<?> c = String[].class;", "}")
                .doTest();
    }

    // --- 2. Trying to find a reachable null-owner symbol via the other three matchers --------

    @Test
    void arrayCloneMethodInvocationDoesNotCrashTheChecker() throws IOException {
        // int[]#clone() resolves through a synthetic array-member symbol, not a real declared
        // method on any user class - a plausible place for a null/synthetic owner.
        helper("java.util.Date")
                .addSourceLines(
                        "Test.java", "class Test {", "  int[] m(int[] arr) {", "    return arr.clone();", "  }", "}")
                .doTest();
    }

    @Test
    void arrayConstructorReferenceDoesNotCrashTheChecker() throws IOException {
        // int[]::new as a java.util.function.IntFunction<int[]> target - a constructor reference
        // to an array type, not a real declared class's constructor.
        helper("java.util.Date")
                .addSourceLines(
                        "Test.java",
                        "import java.util.function.IntFunction;",
                        "class Test {",
                        "  IntFunction<int[]> f = int[]::new;",
                        "}")
                .doTest();
    }

    @Test
    void recordCompactConstructorAndGeneratedAccessorDoNotCrashTheChecker() throws IOException {
        helper("java.util.Date")
                .addSourceLines(
                        "Rec.java",
                        "record Rec(String name) {",
                        "  Rec {",
                        "    java.util.Objects.requireNonNull(name);",
                        "  }",
                        "}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  String m(Rec r) {",
                        "    return new Rec(\"x\").name();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void enumValuesAndValueOfDoNotCrashTheChecker() throws IOException {
        helper("java.util.Date")
                .addSourceLines("Day.java", "enum Day {", "  MONDAY,", "  TUESDAY", "}")
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  Day[] all() {",
                        "    return Day.values();",
                        "  }",
                        "  Day one(String s) {",
                        "    return Day.valueOf(s);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void enumConstantWithBodyOverridingMethodDoesNotCrashTheChecker() throws IOException {
        // Constant-specific class bodies (e.g. `A { void m() {...} }`) create an anonymous
        // subclass of the enum per-constant - calling the overridden method through that constant
        // resolves to a MethodSymbol owned by that anonymous class, not the enum itself.
        helper("java.util.Date")
                .addSourceLines(
                        "Op.java",
                        "enum Op {",
                        "  PLUS {",
                        "    @Override",
                        "    int apply(int a, int b) {",
                        "      return a + b;",
                        "    }",
                        "  };",
                        "  abstract int apply(int a, int b);",
                        "}")
                .addSourceLines(
                        "Test.java", "class Test {", "  int m() {", "    return Op.PLUS.apply(1, 2);", "  }", "}")
                .doTest();
    }

    @Test
    void annotationInterfaceValueMethodCallDoesNotCrashTheChecker() throws IOException {
        // Calling an annotation type's own value() accessor method directly (not as an annotation
        // usage) via an instance obtained through the annotation API - a genuine
        // MethodInvocationTree targeting an annotation-interface method.
        helper("java.util.Date")
                .addSourceLines("Ann.java", "@interface Ann {", "  String value() default \"x\";", "}")
                .addSourceLines(
                        "Test.java",
                        "@Ann(\"hello\")",
                        "class Test {",
                        "  String m() {",
                        "    Ann ann = Test.class.getAnnotation(Ann.class);",
                        "    return ann.value();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void lambdaBodyMethodInvocationAndConstructorAreDetectedNotJustNonCrashing() throws IOException {
        // Not just a non-crash check: confirms usages inside a lambda body are still reported,
        // via the same matchMethodInvocation/matchNewClass paths as any other body statement.
        helperWithShadedClass()
                .addSourceLines(
                        "Test.java",
                        "import java.util.function.Supplier;",
                        "class Test {",
                        "  Supplier<Object> s = () -> {",
                        "    // BUG: Diagnostic contains: com.foo.shaded.Bar is forbidden",
                        "    return new com.foo.shaded.Bar();",
                        "  };",
                        "}")
                .doTest();
    }

    @Test
    void genericMethodReferenceWithInferredTypeArgumentDoesNotCrashTheChecker() throws IOException {
        // Collections.<String>emptyList as a Supplier<List<String>> target - a generic static
        // method reference where the type argument is inferred from the functional-interface
        // target type, not written explicitly at the reference site.
        helper("java.util.Date")
                .addSourceLines(
                        "Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "import java.util.function.Supplier;",
                        "class Test {",
                        "  Supplier<List<String>> s = Collections::emptyList;",
                        "}")
                .doTest();
    }

    @Test
    void covariantReturnTypeOverrideMethodInvocationDoesNotCrashTheChecker() throws IOException {
        // A covariant-return-type override is the source-level analogue of what becomes a
        // synthetic bridge method at bytecode-generation time (well after Error Prone runs).
        helper("java.util.Date")
                .addSourceLines("Base.java", "class Base {", "  Object m() {", "    return null;", "  }", "}")
                .addSourceLines(
                        "Sub.java",
                        "class Sub extends Base {",
                        "  @Override",
                        "  String m() {",
                        "    return \"\";",
                        "  }",
                        "}")
                .addSourceLines("Test.java", "class Test {", "  String m(Sub s) {", "    return s.m();", "  }", "}")
                .doTest();
    }

    private CompilationTestHelper helperWithShadedClass() throws IOException {
        return helper("com.foo.shaded.Bar")
                .addSourceLines("com/foo/shaded/Bar.java", "package com.foo.shaded;", "public class Bar {}");
    }
}
