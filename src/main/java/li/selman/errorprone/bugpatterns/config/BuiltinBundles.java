package li.selman.errorprone.bugpatterns.config;

import java.util.List;

/**
 * Names of the signature bundles shipped as resources under {@code
 * src/main/resources/forbidden-api/<name>.txt}, selectable via {@code -XepOpt:ForbiddenApi:Bundles=}.
 *
 * <p>To add a new bundle: drop a new {@code <name>.txt} resource next to the existing ones (same
 * DSL as a user-supplied signature file) and add its name to {@link #NAMES}.
 */
public final class BuiltinBundles {

    public static final List<String> NAMES = List.of("jdk-system-out", "jdk-default-charset", "jdk-internals");

    private BuiltinBundles() {}

    public static boolean isKnown(String bundleName) {
        return NAMES.contains(bundleName);
    }
}
