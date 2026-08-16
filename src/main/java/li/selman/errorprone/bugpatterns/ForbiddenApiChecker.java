package li.selman.errorprone.bugpatterns;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.annotations.Var;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import li.selman.errorprone.bugpatterns.config.ForbiddenApiConfig;
import li.selman.errorprone.bugpatterns.matcher.ForbiddenApiMatcher;
import li.selman.errorprone.bugpatterns.matcher.UsageKey;
import li.selman.errorprone.bugpatterns.model.ForbiddenSignature;
import org.jspecify.annotations.Nullable;

/**
 * Flags usages of APIs forbidden via {@code -XepOpt:ForbiddenApi:Signatures=<file>} and/or {@code
 * -XepOpt:ForbiddenApi:Bundles=<name>,...}, matching on resolved symbols rather than source text -
 * see the module README for the signature file syntax and built-in bundles.
 *
 * <p>Every syntactic position that can reference a class (field/parameter/return/local types,
 * generic type arguments, {@code extends}/{@code implements}, annotations, {@code throws}, import
 * statements, ...) is ultimately represented by an {@link IdentifierTree} or {@link
 * MemberSelectTree} somewhere in the tree, so those two matchers alone cover every class/package/
 * field usage. Method and constructor invocations/references need their own matchers because a
 * call's target ({@code System.exit}) resolves to a {@code MethodSymbol}, which the class/field
 * matchers deliberately ignore to avoid reporting the same call twice.
 *
 * <p>Configuration is read lazily from {@link VisitorState#errorProneOptions()} on first use,
 * rather than injected via constructor: this checker is discovered as an external plugin (via
 * {@link AutoService @AutoService} on the annotation processor path), and such plugins are
 * instantiated by {@code java.util.ServiceLoader}, which requires a public no-arg constructor - a
 * constructor taking {@code ErrorProneFlags} only works for checkers bundled directly into {@code
 * error_prone_core} and loaded through its own reflective injector, not for plugin jars like this
 * one (confirmed the hard way: a constructor-injected version of this checker threw {@code
 * ServiceConfigurationError: Unable to get public no-arg constructor} the moment a real consuming
 * project tried to compile against it).
 */
@AutoService(BugChecker.class)
@BugPattern(name = "ForbiddenApi", summary = "Usage of a forbidden API", severity = ERROR)
@SuppressWarnings("BugPatternNaming") // "ForbiddenApi" (not "ForbiddenApiChecker") is the name required by spec.
public final class ForbiddenApiChecker extends BugChecker
        implements BugChecker.IdentifierTreeMatcher,
                BugChecker.MemberSelectTreeMatcher,
                BugChecker.MethodInvocationTreeMatcher,
                BugChecker.NewClassTreeMatcher,
                BugChecker.MemberReferenceTreeMatcher {

    // Lazily initialized on first use, then stable for this checker instance's lifetime (one
    // instance is reused across an entire compilation, and ErrorProneFlags don't change
    // mid-compilation). Not shared across concurrent compilations - each gets its own checker
    // instance - so a plain field is sufficient without further synchronization.
    private @Nullable ForbiddenApiMatcher matcher;

    @Override
    public Description matchIdentifier(IdentifierTree tree, VisitorState state) {
        return matchClassOrFieldSymbol(ASTHelpers.getSymbol(tree), tree, state);
    }

    @Override
    public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
        return matchClassOrFieldSymbol(ASTHelpers.getSymbol(tree), tree, state);
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // ASTHelpers.getSymbol never returns null here: Error Prone only runs on trees that
        // already passed FLOW analysis (see --should-stop=ifError=FLOW), by which point every
        // method invocation in a successfully-compiling program has a resolved MethodSymbol.
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(tree);
        return report(
                new UsageKey.MethodUsage(
                        ownerName(symbol), symbol.getSimpleName().toString(), parameterTypeNames(symbol, state)),
                tree,
                state);
    }

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(tree);
        return report(new UsageKey.ConstructorUsage(ownerName(symbol), parameterTypeNames(symbol, state)), tree, state);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(tree);
        UsageKey usage = symbol.isConstructor()
                ? new UsageKey.ConstructorUsage(ownerName(symbol), parameterTypeNames(symbol, state))
                : new UsageKey.MethodUsage(
                        ownerName(symbol), symbol.getSimpleName().toString(), parameterTypeNames(symbol, state));
        return report(usage, tree, state);
    }

    /**
     * Handles class/interface/enum/record/annotation-type references and field/enum-constant
     * references - the two symbol shapes an {@link IdentifierTree}/{@link MemberSelectTree} can
     * resolve to that this checker cares about. Method and constructor symbols are deliberately
     * skipped here: they're handled by the dedicated matchers above, and handling them here too
     * would report the same call/instantiation twice.
     */
    private Description matchClassOrFieldSymbol(Symbol symbol, Tree tree, VisitorState state) {
        if (symbol instanceof Symbol.ClassSymbol classSymbol) {
            return report(new UsageKey.TypeUsage(classSymbol.getQualifiedName().toString()), tree, state);
        }
        if (symbol.getKind() == ElementKind.FIELD || symbol.getKind() == ElementKind.ENUM_CONSTANT) {
            // Unlike every other field/enum-constant symbol, the synthetic FIELD symbol for a
            // primitive/void class literal (e.g. `void.class`) has no real declaring class, so
            // enclosingClass() returns null here - there's no owner to match against, and no
            // signature could ever name this usage anyway.
            Symbol.ClassSymbol owner = ASTHelpers.enclosingClass(symbol);
            if (owner == null) {
                return Description.NO_MATCH;
            }
            return report(
                    new UsageKey.FieldUsage(
                            owner.getQualifiedName().toString(),
                            symbol.getSimpleName().toString()),
                    tree,
                    state);
        }
        return Description.NO_MATCH;
    }

    private Description report(UsageKey usage, Tree tree, VisitorState state) {
        Optional<ForbiddenSignature> match = matcher(state).match(usage);
        if (match.isEmpty()) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree).setMessage(defaultMessage(match.get())).build();
    }

    private ForbiddenApiMatcher matcher(VisitorState state) {
        @Var ForbiddenApiMatcher current = matcher;
        if (current == null) {
            current = ForbiddenApiConfig.load(state.errorProneOptions().getFlags());
            matcher = current;
        }
        return current;
    }

    private static String defaultMessage(ForbiddenSignature signature) {
        return signature
                .message()
                .map(message -> signature.displayName() + " is forbidden. " + message)
                .orElseGet(() -> signature.displayName() + " is forbidden.");
    }

    private static String ownerName(Symbol symbol) {
        return ASTHelpers.enclosingClass(symbol).getQualifiedName().toString();
    }

    private static List<String> parameterTypeNames(Symbol.MethodSymbol symbol, VisitorState state) {
        return symbol.getParameters().stream()
                .map(parameter -> typeName(state.getTypes().erasure(parameter.asType())))
                .toList();
    }

    private static String typeName(Type type) {
        @Var int arrayDepth = 0;
        @Var Type current = type;
        while (current instanceof Type.ArrayType arrayType) {
            arrayDepth++;
            current = arrayType.elemtype;
        }
        String base = current.isPrimitive()
                ? current.toString()
                : current.tsym.getQualifiedName().toString();
        return base + "[]".repeat(arrayDepth);
    }
}
