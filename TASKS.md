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
[done]        2026-08-16 Parser unit tests (grammar, comments, malformed input, messages)
[done]        2026-08-16 Matcher unit tests (package glob boundary/adversarial cases)
[done]        2026-08-16 Error Prone integration tests via CompilationTestHelper (all constructs)
[done]        2026-08-16 Built-in bundle tests (jdk-system-out live compile; charset/internals via config-loader tests)
[done]        2026-08-16 No-duplicate-diagnostic tests
[done]        2026-08-16 Maven consumer example project (written and validated end-to-end)
[done]        2026-08-16 README documentation (syntax, bundles, examples, limitations, comparisons)
[done]        2026-08-16 Remove archetype Placeholder class/test once real API exists
[done]        2026-08-16 Reach 100% line+branch JaCoCo coverage honestly (no untestable defensive code)
[done]        2026-08-16 Code review pass (independent sub-agent) - found real void.class NPE crash
[done]        2026-08-16 QA / adversarial test review pass (independent sub-agent) - 20+ new tests, found duplicate-precedence bug
[done]        2026-08-16 Fix findings from code review + QA
[done]        2026-08-16 Rerun automated tests after fixes (108 tests, 100% coverage, both -Dquick and full verify green)
[done]        2026-08-16 Fix constructor-injection bug found by consumer-example validation (see below)
[done]        2026-08-16 Full build verification (./mvnw verify — Checkstyle/Spotless/NullAway/JaCoCo 100%)
[done]        2026-08-16 Final report: structure, design decisions, limitations, build/test commands
[done]        2026-08-16 Second code-review + QA round after the constructor-injection fix
[done]        2026-08-16 Fix findings from second round (Context-scoped caching, isolated consumer-example validation, README clarifications)
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

## Test-run findings (applied)

- `ForbiddenApiMatcher`'s Var/Refaster/coverage-driven cleanups: removed 4 `if (symbol == null)`
  guards in `ForbiddenApiChecker` (matchMethodInvocation/matchNewClass/matchMemberReference/
  matchClassOrFieldSymbol) - genuinely unreachable given Error Prone only invokes matchers after
  successful FLOW analysis (`--should-stop=ifError=FLOW`), so a resolved tree's symbol is never
  null in a program that compiles at all. Documented with a comment rather than left silently
  removed, since it's a real (if narrow) argument about Error Prone's own invocation contract, not
  just "coverage says so."
- `ForbiddenApiConfig`: consolidated `loadBundle`/`loadFile`'s per-method `catch (IOException)`
  into one at the `load()` entry point (both now `throws IOException` internally), and replaced the
  "bundle resource stream is null" `if`+custom-exception with a plain `Objects.requireNonNull` -
  both branches were unreachable in practice (bundle `.txt` resources are shipped in our own jar
  and kept in sync with `BuiltinBundles.NAMES` by construction) and the consolidation means the one
  remaining catch is already exercised by the missing-signature-file test.
- **Real bug, not just a test artifact**: the custom `maven-surefire-plugin` `<argLine>` added for
  `CompilationTestHelper`'s add-exports/add-opens *replaced* (rather than extended) the
  `-javaagent` flag `jacoco:prepare-agent` injects into that same `argLine` property. Result:
  `mvn verify` reported `[INFO] Skipping JaCoCo execution due to missing execution data file` and
  the 100% coverage gate silently no-op'd instead of failing - the opposite of fail-safe. Fixed by
  prefixing the argLine with `@{argLine}`.
- `maven-javadoc-plugin`'s `<additionalJOption>` is single-valued - repeating it 10 times (once per
  add-exports flag) silently kept only the last one, so the javadoc jar built "successfully" while
  actually being empty (`failOnError=false` swallowed the real failure). Fixed by switching to the
  plural `<additionalOptions><additionalOption>...` list parameter, which is the one that actually
  accepts multiple standalone javadoc-tool options.
- `String.format(String, Object...)` and `String.format("%d", 1)` resolve to the *same* declared
  `MethodSymbol` regardless of call-site arg count (varargs desugars to one array-typed parameter
  in the symbol itself) - an overload-distinction test needs genuinely different overloads (used
  `Integer#valueOf(int)` vs `Integer#valueOf(String)` instead).
- `@Var` cannot be applied to a record component in the record header (conflicts with the implicit
  `final` field) even though that's literally what the `Var` checker's own suggested fix said to
  do - applied `@SuppressWarnings("Var")` to the compact constructor instead, since the reassigned
  variable there is the constructor's own parameter, not the field.

## Second review round findings (applied)

Ran a fresh independent code-review pass and QA pass specifically targeting the three commits
above (the void.class fix, the duplicate-precedence fix, and - highest risk - the constructor-
injection removal), since that much change since the first review round warranted a fresh look
rather than assuming it was all correct.

- **Code review, medium severity, fixed**: the memoized `ForbiddenApiMatcher` was cached on a
  plain instance field, correct only under an unverified assumption ("one checker instance per
  compilation, never reused across compilations with different flags"). The reviewer traced actual
  `ErrorPronePlugins.loadPlugins` behavior and confirmed this holds for the standard
  ServiceLoader-based path, but couldn't rule out unusual build-tool scenarios (Bazel workers,
  Gradle daemon classloader caching) reusing a discovered checker instance/classloader. Fixed by
  switching to `VisitorState.context`-scoped caching (`context.get(ForbiddenApiMatcher.class)`/
  `context.put(...)`) - `Context` is javac/Error Prone's own per-compilation DI container, so this
  is correct regardless of instance-reuse assumptions, removing the whole question rather than just
  documenting it.
- **Code review, minor, accepted as a documented limitation, not fixed**: a malformed
  `Signatures=`/unknown `Bundles=` failure isn't cached, so `ErrorProneScanner`'s per-node
  `handleError` (which catches `Throwable` and continues rather than aborting) means a single
  config typo re-throws - and re-parses the file - on every subsequent AST node instead of failing
  once cleanly. Weighed fixing this (cache the failure too) against the added complexity being hard
  to verify honestly under this project's 100% coverage discipline - `CompilationTestHelper`
  already treats any thrown exception from a matcher as a hard test-harness failure regardless of
  caching, so there's no clean way to write a positive test proving "one clear diagnostic, not
  flooding." Left as-is; noise on a genuinely malformed config is a build-log annoyance, not a
  correctness bug (the build still fails, which is the property that actually matters).
- **QA, real gap, fixed**: `examples/consumer-example` had only ever been built from *inside* this
  repository, which silently supplied its own ancestor `.mvn/jvm.config` (Maven discovers
  `.mvn/jvm.config` by walking up from the working directory regardless of reactor/module
  membership) - so the "validated end-to-end" claim from the constructor-injection fix was real but
  incomplete: it was never tested the way an actual separate external project would experience it.
  The QA agent proved this by copying the example to a directory with no ancestor `.mvn` and
  watching it fail with `IllegalAccessError: module jdk.compiler does not export
  com.sun.tools.javac.api to unnamed module` before Error Prone even runs. Fixed by giving
  `examples/consumer-example` its own `.mvn/jvm.config`, then re-validated both the pass and fail
  path from a genuinely isolated directory (confirmed no ancestor `.mvn` on the path) myself.
- **QA, confirmed existing (not new) behavior, documented**: verified via `javap` against the real
  jar that `ErrorProneFlags` stores flags in a plain `HashMap` keyed by flag name, so two *separate*
  `-XepOpt:ForbiddenApi:Signatures=` flag occurrences on one command line don't merge - the second
  silently replaces the first, and the first file's signatures never load. Multiple files must be
  comma-joined into a single `Signatures=` value instead, which does work and does exhibit correct
  last-wins precedence. Clarified in the README (was ambiguously worded to suggest repeated flags
  might merge).
- Confirmed solid, no changes needed: the `void.class`-fix theory (that
  `matchMethodInvocation`/`matchNewClass`/`matchMemberReference` can never observe a null-owner
  symbol) held up under both agents' adversarial probing - bridge-adjacent covariant overrides,
  record compact constructors/accessors, enum `values()`/`valueOf()`/constant bodies, annotation
  `value()` calls, `int[]::new`, generic method references, lambda bodies - none crash. The reverse-
  iteration duplicate-precedence fix was confirmed correct for 3+ overlapping signatures (not just
  2), including a live compile combining a bundle with two comma-joined `Signatures=` files.
- New regression tests from this round (36 tests, all passing) kept under
  `src/test/java/li/selman/errorprone/bugpatterns/qa2/` - `NullOwnerAndClassLiteralQa2Test`,
  `ThreeWayDuplicatePrecedenceQa2Test`, `LazyConfigLoadingQa2Test`.

## Consumer-example validation finding (critical, applied)

Actually building `examples/consumer-example` against the locally-`install`-ed jar (as a real,
separate Maven project would) surfaced a bug that **every** `CompilationTestHelper`-based test in
this repo had completely missed:

`ForbiddenApiChecker`'s `@Inject public ForbiddenApiChecker(ErrorProneFlags flags)` constructor
crashed with `ServiceConfigurationError: ... Unable to get public no-arg constructor` the moment
javac tried to discover it as a plugin. Root cause: a checker registered via
`@AutoService(BugChecker.class)` (i.e. any external plugin jar on the annotation processor path,
which is how *every* real consumer uses this library) is discovered by
`com.google.errorprone.ErrorPronePlugins.loadPlugins` through plain `java.util.ServiceLoader`,
which mandates a public no-arg constructor per the Java SPI contract - full stop, no exceptions.
The `ErrorProneFlags`-constructor-injection pattern only works for checkers loaded through Error
Prone's own reflective `ErrorProneInjector`, which is how `CompilationTestHelper.newInstance(...)`
constructs checkers internally - a *different, more permissive* code path than real plugin
discovery, which is exactly why 108 passing tests never caught this.

Fixed by switching to a no-arg constructor and reading `ErrorProneFlags` lazily from
`VisitorState.errorProneOptions().getFlags()` inside a memoizing `matcher(VisitorState)` accessor
(threaded through every matcher method), instead of at construction time. Re-verified end-to-end
after the fix: `examples/consumer-example` now compiles cleanly against the real published jar,
and - copied `Demo.java`, added a `java.util.Date` field, ran `mvn compile`, confirmed it fails with
exactly `[ForbiddenApi] java.util.Date is forbidden. Use java.time instead`, then restored the file
and confirmed a clean compile again - genuinely exercises both the pass and fail paths, not just
the happy path.

This is the strongest argument in this whole project for why "the automated test suite passes"
alone was never going to be sufficient to call this done, and why the explicit "Maven consumer
example has been validated" step in the task brief mattered.

## Code review + QA findings (applied)

Both an independent code-review sub-agent and an independent QA sub-agent reviewed the
implementation; findings below, verified myself before fixing (reproduced the crash with a new
test first, confirmed the fix, didn't just take the report on faith):

- **Real NPE crash, confirmed independently**: `void.class` (and any other primitive/void class
  literal) resolves to a synthetic `FIELD` symbol whose `ASTHelpers.enclosingClass()` returns
  `null` (no real declaring class) - unlike every other field/enum-constant symbol. The checker's
  `matchClassOrFieldSymbol` unconditionally dereferenced that result, crashing on any compilation
  unit containing e.g. `Method.getReturnType() == void.class`, with *zero* signatures configured.
  Reproduced with `ForbiddenApiCheckerTest#voidClassLiteralDoesNotCrashTheChecker` (crashed before
  the fix, confirmed via a standalone `-Dtest=` run), then fixed with a narrowly-scoped null check
  at that one call site only - not a broader change to `ownerName()`, since the method/constructor/
  member-reference call sites have no known reachable null-owner scenario and adding an untestable
  guard there would violate the "no speculative defensive code" coverage discipline applied
  throughout this project.
- **Duplicate-signature precedence was inconsistent, contradicting `ForbiddenApiMatcher`'s own
  javadoc**: `ClassSignature`/`FieldSignature` (map-keyed) correctly let a later duplicate win, but
  `PackageSignature`/`MethodSignature`/`ConstructorSignature` (list-keyed, first-match-wins linear
  scan) let the *earlier* one win - meaning a user's own `Signatures=` file could override a bundle's
  class/field ban but never its package/method/constructor ban. Fixed by scanning those three lists
  in reverse order (checking the most-recently-configured entry first), making all five signature
  kinds consistently last-wins. The QA and code-review agents independently found the same root
  cause; both agents' test files (kept, updated to assert the corrected behavior) live under
  `src/test/java/li/selman/errorprone/bugpatterns/qa/`.
- Minor: `ForbiddenApiConfig.loadBundle` double-closed its resource stream (once via its own
  try-with-resources, once via `readLines`'s nested one) - harmless for standard JDK streams but
  sloppy; simplified to a single close site.
- Minor: `ForbiddenApiMatcher`'s `methodsByKey`/`constructorsByKey` weren't defensively copied to
  immutable collections like every other field on the class - fixed for consistency (not an active
  bug: nothing mutates them post-construction today).
- **Verified, not just trusted**: the code-review agent's claim that `case SOME_CONSTANT ->` in a
  Java 21+ pattern-matching `switch` *does* route through `IdentifierTreeMatcher` (contradicting an
  earlier "unverified" caveat carried over from the architecture review) - added
  `ForbiddenApiCheckerTest#enumConstantInPatternMatchingSwitchLabelIsDetected` to confirm this
  directly against the real compiler rather than taking either agent's word for it, and removed the
  now-incorrect caveat from the README.
- Separately, the QA agent found `-Dquick test` crashed Surefire outright (unrelated to the checker
  itself): the `@{argLine}` surefire config added earlier for `CompilationTestHelper` support
  resolves to the literal unresolved token `@{argLine}` when `jacoco:prepare-agent` hasn't run to
  populate that property (i.e. whenever the `qa` profile is inactive), which `java` then tries to
  open as an `@argfile` and fails. Fixed with an empty default `<argLine/>` property.
- 20+ additional QA-authored tests for constructs not previously covered (generic method erasure,
  varargs-vs-array matching, nested-class parameter types, inherited-method bans via subclass
  reference, more package-glob boundary cases, CRLF/tab-tolerant parsing, records, `var` locals,
  try-with-resources, anonymous/local classes, sealed `permits`, type-use annotations,
  `@SuppressWarnings("ForbiddenApi")`) all passed as-is - kept as permanent regression coverage in
  `src/test/java/li/selman/errorprone/bugpatterns/qa/ForbiddenApiCheckerQaTest.java`.

## Repo-level finding (applied)

- `git init` defaulted the local branch to `master`, but every workflow (`ci.yml`, `pages.yml`)
  and every badge URL in `README.md`/`docs/index.html` assumes `main` (the archetype's convention).
  Result: CI would never have triggered on push. Renamed the branch to `main`, pushed it, set it as
  the GitHub default branch, and deleted the stale `master` ref.

## Notes

- JDK: build with Temurin 25 (`~/Library/Java/JavaVirtualMachines/temurin-25.0.3`), NOT the
  system default JDK 26 (Homebrew) — Error Prone 2.50.0 compatibility with JDK 26 is unverified;
  25 is the archetype's own default and known-good.
- groupId=`li.selman.error-prone`, artifactId=`bugpatterns`, package=`li.selman.errorprone.bugpatterns`.
- GitHub repo: `haisi/error-prone-support` (created private; pushed after initial scaffold commit).
- Archetype's `qa` Maven profile enforces: Spotless (palantir-java-format), Checkstyle, NullAway
  (`-Werror`), and 100% line+branch JaCoCo coverage on `verify`. All new code must satisfy this.
