package li.selman.errorprone.bugpatterns.config;

import com.google.errorprone.ErrorProneFlags;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import li.selman.errorprone.bugpatterns.matcher.ForbiddenApiMatcher;
import li.selman.errorprone.bugpatterns.model.ForbiddenSignature;

/**
 * The Error Prone integration layer's config loader: reads the {@code Signatures}/{@code Bundles}
 * flags, resolves them to signature files/bundle resources, parses each with {@link
 * ForbiddenSignatureParser}, and builds a {@link ForbiddenApiMatcher} from the combined result.
 */
public final class ForbiddenApiConfig {

    private static final String SIGNATURES_FLAG = "ForbiddenApi:Signatures";
    private static final String BUNDLES_FLAG = "ForbiddenApi:Bundles";

    private ForbiddenApiConfig() {}

    public static ForbiddenApiMatcher load(ErrorProneFlags flags) {
        try {
            List<ForbiddenSignature> signatures = new ArrayList<>();
            for (String bundleName : flags.getListOrEmpty(BUNDLES_FLAG)) {
                signatures.addAll(loadBundle(bundleName));
            }
            for (String path : flags.getListOrEmpty(SIGNATURES_FLAG)) {
                signatures.addAll(loadFile(path));
            }
            return ForbiddenApiMatcher.of(signatures);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load ForbiddenApi configuration", e);
        }
    }

    private static List<ForbiddenSignature> loadBundle(String bundleName) throws IOException {
        if (!BuiltinBundles.isKnown(bundleName)) {
            throw new ForbiddenSignatureParseException(
                    "Unknown ForbiddenApi bundle '" + bundleName + "'. Known bundles: " + BuiltinBundles.NAMES);
        }
        String resourcePath = "/forbidden-api/" + bundleName + ".txt";
        // Bundle resources are shipped in this jar's own classpath and kept in sync with
        // BuiltinBundles.NAMES by construction, so a missing resource here would be a packaging
        // bug rather than a reachable user-facing state.
        try (InputStream in = Objects.requireNonNull(ForbiddenApiConfig.class.getResourceAsStream(resourcePath))) {
            return ForbiddenSignatureParser.parse("bundle:" + bundleName, readLines(in));
        }
    }

    private static List<ForbiddenSignature> loadFile(String path) throws IOException {
        return ForbiddenSignatureParser.parse(path, Files.readAllLines(Path.of(path), StandardCharsets.UTF_8));
    }

    private static List<String> readLines(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }
}
