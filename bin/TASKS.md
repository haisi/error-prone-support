# Task plan — error-prone-support / ForbiddenApi checker

Tracks execution of the plan in `prompt.md`. Update statuses as work progresses.
States: `open`, `in_progress`, `done`.

```text
[done]        2026-08-16 Set up Maven module via java-lib-archetype, verify build with JDK 25
[done]        2026-08-16 Architecture review (Java Library Expert agent) of proposed design
[done]        2026-08-16 Add error_prone_check_api (provided) / error_prone_test_helpers / auto-service deps
[done]        2026-08-16 Define internal model (ForbiddenSignature sealed hierarchy of records)
[done]        2026-08-16 Implement ForbiddenSignatureParser (signature DSL + parse errors)
[done]        2026-08-16 Implement ForbiddenApiMatcher (pure/symbol-free, string-keyed; per architect review)
[done]        2026-08-16 Implement bundle loading mechanism (Bundles=... option, ErrorProneFlags.getListOrEmpty)
[done]        2026-08-16 Author built-in bundles: jdk-system-out, jdk-default-charset, jdk-internals (verified against JDK 25 via javap)
[done]        2026-08-16 Implement ForbiddenApiChecker (5 tree matchers: Identifier/MemberSelect/MethodInvocation/NewClass/MemberReference)
[done]        2026-08-16 Register checker via @AutoService(BugChecker.class) (per architect review)
[open]        2026-08-16 Parser unit tests (grammar, comments, malformed input, messages)
[open]        2026-08-16 Matcher unit tests (package glob boundary/adversarial cases)
[open]        2026-08-16 Error Prone integration tests via CompilationTestHelper (all constructs)
[open]        2026-08-16 Built-in bundle tests
[open]        2026-08-16 No-duplicate-diagnostic tests
[open]        2026-08-16 Maven consumer example project
[open]        2026-08-16 README documentation
[open]        2026-08-16 Remove archetype Placeholder class/test once real API exists
[open]        2026-08-16 Code review pass (independent sub-agent)
[open]        2026-08-16 QA / adversarial test review pass (independent sub-agent)
[open]        2026-08-16 Fix findings from code review + QA
[open]        2026-08-16 Full build verification (./mvnw verify — Checkstyle/Spotless/NullAway/JaCoCo 100%)
[open]        2026-08-16 Final report: structure, design decisions, limitations, build/test commands
```

## Architecture review findings (applied)

- Matcher must stay javac-symbol-free (translate `Symbol` -> plain `SymbolKey`-like strings in the
  checker layer) so it's unit-testable without a live compiler. Already the plan; confirmed.
- Use `ErrorProneFlags.getList(String)` for `Signatures=`/`Bundles=`, not hand-rolled splitting.
- Need a 5th tree matcher: `MemberReferenceTreeMatcher` (`System::exit`, `Foo::new`) — method/
  constructor references aren't `MethodInvocationTree`/`NewClassTree` and were missed initially.
- Imports are already visited by `IdentifierTreeMatcher`/`MemberSelectTreeMatcher` (javac's
  `TreeScanner.visitImport` scans the qualified identifier) — an `import` of a forbidden class
  will itself be flagged at the import's own position. This is a *separate* usage site from any
  in-body reference, not a duplicate diagnostic; add an explicit test for an unused-but-imported
  forbidden class.
- `case MONDAY ->` (Java 21+ pattern-matching switch, `ConstantCaseLabelTree`) is NOT verified to
  route through `IdentifierTreeMatcher` — documented as an unverified/unsupported limitation
  rather than assumed to work.
- Avoid `default: throw new AssertionError(...)` in exhaustive switches over the sealed
  `ForbiddenSignature` hierarchy / `ElementKind` — unreachable branches fail the 100% JaCoCo gate
  honestly; use guard-clause `Optional.empty()` returns instead.
- `error_prone_check_api` moved to `provided` scope (consumer supplies it transitively via
  `error_prone_core` on their annotationProcessorPath — bundling it risks processor-path skew).
- Registration via `@AutoService(BugChecker.class)` (added `auto-service` as `provided` dependency
  + annotationProcessorPath entry) instead of a hand-maintained `META-INF/services` file.
- Bundle `.txt` resources loaded via `ForbiddenApiConfig.class.getResourceAsStream(...)`, not the
  thread context classloader.

## Build-config findings (applied, not from the architect review)

- `ErrorProneFlags` has no `getList` method; the real API (verified via `javap` against the actual
  2.50.0 jar) is `getListOrEmpty(String)` returning `ImmutableList<String>` directly. Flag keys
  include the check name prefix (`"ForbiddenApi:Signatures"`, not just `"Signatures"`) - confirmed
  by inspecting `ErrorProneOptions.class`'s string constants (splits on the whole `-XepOpt:` tail).
- Compiling this module's own sources needs `--add-exports` for `com.sun.tools.javac.{code,comp}`
  (writing a `BugChecker` means importing `Symbol`/`Type` directly) - this only affected the base
  `mvn compile`/`-Dquick` path, since the archetype's `--add-exports` set previously lived only
  inside the `qa` profile's compiler config. Added a base-build `maven-compiler-plugin` block with
  just the exports, unconditionally.
- javac rejects `--add-exports` combined with `--release` (the archetype's default via
  `maven.compiler.release=25`). Switched this project to `maven.compiler.source`/`target=25`
  instead - a deliberate, documented deviation from the archetype default, unlike a typical
  generated project that never touches javac internals.
- `CompilationTestHelper` tests run `javac` in-process inside the (forked) Surefire JVM, which does
  not inherit `.mvn/jvm.config` - added a matching `argLine` to the base `maven-surefire-plugin`
  config so tests get the same `--add-exports`/`--add-opens` set.
- **Archetype bug** (in `haisi/java-lib-archetype` itself, not this project): `archetype:generate`
  never emits `.gitignore`, confirmed by regenerating a throwaway `com.example:example-lib` project
  from a clean directory - `.editorconfig` and `.github/` came out fine, `.gitignore` did not, even
  though it's declared in the archetype's own `archetype-metadata.xml` unfiltered fileset. Worked
  around here by copying the archetype's own `archetype-resources/.gitignore` verbatim (it has no
  template placeholders to fill in). Worth reporting upstream in that repo separately.
- Wrote XML comments containing literal `--` sequences (inside `--add-exports ...` mentions) three
  times while editing this pom, each time silently breaking XML comment syntax (`--` is illegal
  inside an XML comment body) and making the whole POM unparseable with a confusing cascade of
  unrelated-looking IDE errors. Fixed by rewording comments to avoid literal `--`; worth remembering
  for any future edits mentioning CLI double-dash flags in pom.xml comments.

## Notes

- JDK: build with Temurin 25 (`~/Library/Java/JavaVirtualMachines/temurin-25.0.3`), NOT the
  system default JDK 26 (Homebrew) — Error Prone 2.50.0 compatibility with JDK 26 is unverified;
  25 is the archetype's own default and known-good.
- groupId=`li.selman.error-prone`, artifactId=`bugpatterns`, package=`li.selman.errorprone.bugpatterns`.
- GitHub repo: `haisi/error-prone-support` (created private; pushed after initial scaffold commit).
- Archetype's `qa` Maven profile enforces: Spotless (palantir-java-format), Checkstyle, NullAway
  (`-Werror`), and 100% line+branch JaCoCo coverage on `verify`. All new code must satisfy this.
