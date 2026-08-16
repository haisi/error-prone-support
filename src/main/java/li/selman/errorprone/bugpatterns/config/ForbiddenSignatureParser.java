package li.selman.errorprone.bugpatterns.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import li.selman.errorprone.bugpatterns.model.ClassSignature;
import li.selman.errorprone.bugpatterns.model.ConstructorSignature;
import li.selman.errorprone.bugpatterns.model.FieldSignature;
import li.selman.errorprone.bugpatterns.model.ForbiddenSignature;
import li.selman.errorprone.bugpatterns.model.MethodSignature;
import li.selman.errorprone.bugpatterns.model.PackageSignature;

/**
 * Parses the line-oriented signature DSL (inspired by {@code policeman-tools/forbidden-apis}) into
 * {@link ForbiddenSignature} values.
 *
 * <pre>{@code
 * # comment
 * com.mycompany.shaded.**              # package + all descendant packages
 * com.mycompany.shaded.*               # only classes directly in the package
 * java.util.Date                       # exact class
 * java.lang.System#out                 # field
 * java.lang.System#exit(int)           # method
 * java.lang.Integer#<init>(int)        # constructor
 * java.util.Date @ Use java.time instead   # optional custom message
 * }</pre>
 *
 * Blank lines and lines starting with {@code #} are ignored. This class performs no I/O: callers
 * are responsible for reading the signature file/resource into lines and supplying a {@code
 * sourceName} (typically the file path) used to produce {@code file:line} parse errors.
 */
public final class ForbiddenSignatureParser {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final Pattern IDENTIFIER = Pattern.compile(IDENTIFIER_PATTERN);
    private static final Pattern DOTTED_NAME = Pattern.compile(IDENTIFIER_PATTERN + "(\\." + IDENTIFIER_PATTERN + ")*");
    private static final Pattern PARAMETER_TYPE = Pattern.compile(DOTTED_NAME.pattern() + "(\\[\\])*");

    private ForbiddenSignatureParser() {}

    /**
     * Parses every non-blank, non-comment line into a {@link ForbiddenSignature}.
     *
     * @param sourceName identifies the origin of {@code lines} (e.g. a file path or bundle name),
     *     used only to produce {@code file:line} messages in {@link ForbiddenSignatureParseException}
     * @throws ForbiddenSignatureParseException if any line is malformed
     */
    public static List<ForbiddenSignature> parse(String sourceName, List<String> lines) {
        List<ForbiddenSignature> signatures = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            signatures.add(parseLine(sourceName, lineNumber, line));
        }
        return List.copyOf(signatures);
    }

    private static ForbiddenSignature parseLine(String sourceName, int lineNumber, String line) {
        String signaturePart;
        Optional<String> message;
        int at = line.indexOf('@');
        if (at < 0) {
            signaturePart = line;
            message = Optional.empty();
        } else {
            signaturePart = line.substring(0, at).strip();
            String messageText = line.substring(at + 1).strip();
            if (messageText.isEmpty()) {
                throw new ForbiddenSignatureParseException(sourceName, lineNumber, "empty custom message after '@'");
            }
            message = Optional.of(messageText);
        }
        if (signaturePart.isEmpty()) {
            throw new ForbiddenSignatureParseException(sourceName, lineNumber, "missing signature before '@'");
        }
        return parseSignature(sourceName, lineNumber, signaturePart, message);
    }

    private static ForbiddenSignature parseSignature(
            String sourceName, int lineNumber, String signature, Optional<String> message) {
        if (signature.endsWith(".**")) {
            String packageName = signature.substring(0, signature.length() - ".**".length());
            requireDottedName(sourceName, lineNumber, packageName, "package name");
            return new PackageSignature(packageName, true, message);
        }
        if (signature.endsWith(".*")) {
            String packageName = signature.substring(0, signature.length() - ".*".length());
            requireDottedName(sourceName, lineNumber, packageName, "package name");
            return new PackageSignature(packageName, false, message);
        }

        int hash = signature.indexOf('#');
        if (hash < 0) {
            requireDottedName(sourceName, lineNumber, signature, "class name");
            return new ClassSignature(signature, message);
        }

        String ownerClassName = signature.substring(0, hash);
        String member = signature.substring(hash + 1);
        requireDottedName(sourceName, lineNumber, ownerClassName, "class name");
        if (member.isEmpty()) {
            throw new ForbiddenSignatureParseException(sourceName, lineNumber, "missing member name after '#'");
        }

        if (member.startsWith("<init>")) {
            String parameterList = member.substring("<init>".length());
            List<String> parameterTypes = parseParameterList(sourceName, lineNumber, parameterList, "constructor");
            return new ConstructorSignature(ownerClassName, parameterTypes, message);
        }

        int openParen = member.indexOf('(');
        if (openParen < 0) {
            requireIdentifier(sourceName, lineNumber, member, "field name");
            return new FieldSignature(ownerClassName, member, message);
        }

        String methodName = member.substring(0, openParen);
        requireIdentifier(sourceName, lineNumber, methodName, "method name");
        String parameterList = member.substring(openParen);
        List<String> parameterTypes = parseParameterList(sourceName, lineNumber, parameterList, "method");
        return new MethodSignature(ownerClassName, methodName, parameterTypes, message);
    }

    private static List<String> parseParameterList(
            String sourceName, int lineNumber, String parameterList, String kind) {
        if (!parameterList.startsWith("(") || !parameterList.endsWith(")")) {
            throw new ForbiddenSignatureParseException(
                    sourceName,
                    lineNumber,
                    "malformed " + kind + " parameter list '" + parameterList
                            + "', expected e.g. '(int, java.lang.String)'");
        }
        String inner = parameterList.substring(1, parameterList.length() - 1).strip();
        if (inner.isEmpty()) {
            return List.of();
        }

        List<String> parameterTypes = new ArrayList<>();
        for (String rawParameterType : inner.split(",", -1)) {
            String parameterType = rawParameterType.strip();
            if (!PARAMETER_TYPE.matcher(parameterType).matches()) {
                throw new ForbiddenSignatureParseException(
                        sourceName, lineNumber, "malformed parameter type '" + parameterType + "'");
            }
            parameterTypes.add(parameterType);
        }
        return List.copyOf(parameterTypes);
    }

    private static void requireDottedName(String sourceName, int lineNumber, String name, String what) {
        if (!DOTTED_NAME.matcher(name).matches()) {
            throw new ForbiddenSignatureParseException(sourceName, lineNumber, "invalid " + what + " '" + name + "'");
        }
    }

    private static void requireIdentifier(String sourceName, int lineNumber, String name, String what) {
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new ForbiddenSignatureParseException(sourceName, lineNumber, "invalid " + what + " '" + name + "'");
        }
    }
}
