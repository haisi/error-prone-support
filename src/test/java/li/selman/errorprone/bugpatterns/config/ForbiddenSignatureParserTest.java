package li.selman.errorprone.bugpatterns.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import li.selman.errorprone.bugpatterns.model.ClassSignature;
import li.selman.errorprone.bugpatterns.model.ConstructorSignature;
import li.selman.errorprone.bugpatterns.model.FieldSignature;
import li.selman.errorprone.bugpatterns.model.ForbiddenSignature;
import li.selman.errorprone.bugpatterns.model.MethodSignature;
import li.selman.errorprone.bugpatterns.model.PackageSignature;
import org.junit.jupiter.api.Test;

final class ForbiddenSignatureParserTest {

    private static List<ForbiddenSignature> parse(String... lines) {
        return ForbiddenSignatureParser.parse("test.txt", List.of(lines));
    }

    @Test
    void ignoresBlankLinesAndComments() {
        assertThat(parse("", "   ", "# a comment", "  # indented comment")).isEmpty();
    }

    @Test
    void parsesExactClass() {
        assertThat(parse("java.util.Date")).containsExactly(new ClassSignature("java.util.Date", Optional.empty()));
    }

    @Test
    void parsesNestedClassName() {
        assertThat(parse("java.util.Map.Entry"))
                .containsExactly(new ClassSignature("java.util.Map.Entry", Optional.empty()));
    }

    @Test
    void parsesPackageWithAllDescendants() {
        assertThat(parse("com.mycompany.shaded.**"))
                .containsExactly(new PackageSignature("com.mycompany.shaded", true, Optional.empty()));
    }

    @Test
    void parsesPackageDirectChildrenOnly() {
        assertThat(parse("com.mycompany.shaded.*"))
                .containsExactly(new PackageSignature("com.mycompany.shaded", false, Optional.empty()));
    }

    @Test
    void parsesField() {
        assertThat(parse("java.lang.System#out"))
                .containsExactly(new FieldSignature("java.lang.System", "out", Optional.empty()));
    }

    @Test
    void parsesZeroArgMethod() {
        assertThat(parse("java.lang.String#getBytes()"))
                .containsExactly(new MethodSignature("java.lang.String", "getBytes", List.of(), Optional.empty()));
    }

    @Test
    void parsesMethodWithParameters() {
        assertThat(parse("java.lang.System#exit(int)"))
                .containsExactly(new MethodSignature("java.lang.System", "exit", List.of("int"), Optional.empty()));
    }

    @Test
    void parsesMethodWithMultipleParametersAndTrimsWhitespace() {
        assertThat(parse("java.lang.String#format( java.lang.String , java.lang.Object[] )"))
                .containsExactly(new MethodSignature(
                        "java.lang.String",
                        "format",
                        List.of("java.lang.String", "java.lang.Object[]"),
                        Optional.empty()));
    }

    @Test
    void parsesConstructor() {
        assertThat(parse("java.lang.Integer#<init>(int)"))
                .containsExactly(new ConstructorSignature("java.lang.Integer", List.of("int"), Optional.empty()));
    }

    @Test
    void parsesZeroArgConstructor() {
        assertThat(parse("java.lang.Object#<init>()"))
                .containsExactly(new ConstructorSignature("java.lang.Object", List.of(), Optional.empty()));
    }

    @Test
    void parsesCustomMessage() {
        assertThat(parse("java.util.Date @ Use java.time instead"))
                .containsExactly(new ClassSignature("java.util.Date", Optional.of("Use java.time instead")));
    }

    @Test
    void parsesCustomMessageOnField() {
        assertThat(parse("java.lang.System#out @ Use the configured logger instead"))
                .containsExactly(new FieldSignature(
                        "java.lang.System", "out", Optional.of("Use the configured logger instead")));
    }

    @Test
    void parsesCustomMessageOnPackage() {
        assertThat(parse("com.mycompany.shaded.** @ Shaded dependencies are implementation details"))
                .containsExactly(new PackageSignature(
                        "com.mycompany.shaded", true, Optional.of("Shaded dependencies are implementation details")));
    }

    @Test
    void multipleLinesProduceMultipleSignaturesInOrder() {
        assertThat(parse("java.util.Date", "# comment", "", "java.lang.System#out"))
                .containsExactly(
                        new ClassSignature("java.util.Date", Optional.empty()),
                        new FieldSignature("java.lang.System", "out", Optional.empty()));
    }

    @Test
    void duplicateSignaturesAreBothReturned() {
        assertThat(parse("java.util.Date", "java.util.Date @ second message"))
                .containsExactly(
                        new ClassSignature("java.util.Date", Optional.empty()),
                        new ClassSignature("java.util.Date", Optional.of("second message")));
    }

    @Test
    void rejectsInvalidClassName() {
        assertThatThrownBy(() -> parse("1invalid.Name"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("test.txt:1")
                .hasMessageContaining("invalid class name");
    }

    @Test
    void rejectsMissingMemberAfterHash() {
        assertThatThrownBy(() -> parse("java.lang.System#"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("test.txt:1")
                .hasMessageContaining("missing member name");
    }

    @Test
    void rejectsMethodWithUnmatchedOpenParen() {
        assertThatThrownBy(() -> parse("java.lang.System#exit(int"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("malformed method parameter list");
    }

    @Test
    void rejectsConstructorMissingParens() {
        assertThatThrownBy(() -> parse("java.lang.Integer#<init>"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("malformed constructor parameter list");
    }

    @Test
    void rejectsMalformedParameterType() {
        assertThatThrownBy(() -> parse("java.lang.System#exit(1nt)"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("malformed parameter type");
    }

    @Test
    void rejectsInvalidMethodName() {
        assertThatThrownBy(() -> parse("java.lang.System#9exit(int)"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("invalid method name");
    }

    @Test
    void rejectsEmptyMessageAfterAt() {
        assertThatThrownBy(() -> parse("java.util.Date @   "))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("empty custom message");
    }

    @Test
    void rejectsMissingSignatureBeforeAt() {
        assertThatThrownBy(() -> parse("  @ some message"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("missing signature");
    }

    @Test
    void rejectsInvalidPackageGlob() {
        assertThatThrownBy(() -> parse("com..foo.**"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("invalid package name");
    }

    @Test
    void errorMessageIncludesLineNumberOfOffendingLine() {
        assertThatThrownBy(() -> parse("java.util.Date", "", "# ok", "1bad.Name"))
                .isInstanceOf(ForbiddenSignatureParseException.class)
                .hasMessageContaining("test.txt:4");
    }
}
