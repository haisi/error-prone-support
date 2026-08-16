# bugpatterns

[![CI](https://github.com/haisi/error-prone-support/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/haisi/error-prone-support/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/haisi/error-prone-support/badge.svg?branch=main)](https://coveralls.io/github/haisi/error-prone-support?branch=main)
[![Maven Central](https://img.shields.io/maven-central/v/li.selman.error-prone/bugpatterns.svg)](https://central.sonatype.com/artifact/li.selman.error-prone/bugpatterns)
[![Javadoc](https://javadoc.io/badge2/li.selman.error-prone/bugpatterns/javadoc.svg)](https://javadoc.io/doc/li.selman.error-prone/bugpatterns)
[![License](https://img.shields.io/github/license/haisi/error-prone-support)](LICENSE)
[![Mutation Score](https://haisi.github.io/error-prone-support/pit/badge.svg)](https://haisi.github.io/error-prone-support/pit/)

[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=bugs)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=haisi_error-prone-support&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=haisi_error-prone-support)

Custom Error Prone bug patterns, starting with a configurable `ForbiddenApi` checker for
signature-based API bans.

[**Website**](https://haisi.github.io/error-prone-support/)

## What it does

`ForbiddenApi` is an [Error Prone](https://errorprone.info) `BugChecker` that fails the build when
your code uses an API you've explicitly banned - a class, package, field, method, or constructor -
configured through a plain-text signature file. It's inspired by
[`policeman-tools/forbidden-apis`](https://github.com/policeman-tools/forbidden-apis), but instead
of scanning compiled bytecode after the fact, it runs *during* `javac`/Error Prone compilation and
matches against **resolved symbols** - so it catches a forbidden class whether it's used by its
simple name, fully qualified, imported, or referenced generically, and it won't be fooled by two
classes that merely share a textual prefix.

Ships with three built-in bundles (`jdk-system-out`, `jdk-default-charset`, `jdk-internals`) for
common bans, and lets you add project-specific ones via your own signature file.

## Installation

Not yet published to Maven Central (see [Releasing](#releasing) below for how that eventually
happens). Until then, build and install it locally:

```shell
./mvnw install -Dquick -DskipTests
```

## Maven configuration

**Note**: `javac` rejects any `--add-exports` compiler flag combined with `maven.compiler.release`
(confirmed empirically - not limited to any particular package), so if your project sets
`maven.compiler.release`, switch to `maven.compiler.source`/`maven.compiler.target` instead, as the
example below and [`examples/consumer-example`](examples/consumer-example) do.

**Note**: the `compilerArgs` below aren't enough on their own on JDK 16+ - Maven's own JVM (the one
running the in-process compiler) also needs the same `--add-exports`/`--add-opens` set, via a
`.mvn/jvm.config` file at your project root (see
[`examples/consumer-example/.mvn/jvm.config`](examples/consumer-example/.mvn/jvm.config) for a
working copy). Without it, compilation fails with `IllegalAccessError: ... module jdk.compiler does
not export ... to unnamed module` before Error Prone ever runs - confirmed by testing this
project's own consumer example from a directory with no such file.

Add `error_prone_core` and this project's `bugpatterns` artifact to your compiler plugin's
`annotationProcessorPaths`, and enable the checker via `-Xep:ForbiddenApi:ERROR` and
`-XepOpt:ForbiddenApi:Signatures=`/`Bundles=`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs>
            <!-- Required to run any Error Prone plugin on JDK 16+. -->
            <arg>--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED</arg>
            <arg>--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED</arg>
            <arg>--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED</arg>
            <arg>--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED</arg>
            <arg>--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED</arg>
            <arg>--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED</arg>
            <arg>--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
            <arg>--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>
            <arg>-XDcompilePolicy=simple</arg>
            <arg>--should-stop=ifError=FLOW</arg>
            <arg>
                -Xplugin:ErrorProne -Xep:ForbiddenApi:ERROR
                -XepOpt:ForbiddenApi:Signatures=${project.basedir}/config/forbidden-apis.txt
                -XepOpt:ForbiddenApi:Bundles=jdk-system-out,jdk-default-charset
            </arg>
        </compilerArgs>
        <annotationProcessorPaths>
            <path>
                <groupId>com.google.errorprone</groupId>
                <artifactId>error_prone_core</artifactId>
                <version>2.50.0</version>
            </path>
            <path>
                <groupId>li.selman.error-prone</groupId>
                <artifactId>bugpatterns</artifactId>
                <version>0.1.0-SNAPSHOT</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

A complete, buildable copy of this is in [`examples/consumer-example`](examples/consumer-example)
- see its README for how to actually run it end to end, including watching it reject a violation.

## Signature syntax

Line-oriented, inspired by `forbidden-apis`. Blank lines and lines starting with `#` are ignored.

```text
# Package, and every descendant package, however deeply nested
com.mycompany.shaded.**

# Package - only classes directly inside it, not descendant packages
com.mycompany.shaded.*

# Exact class (dotted form, including for nested classes: java.util.Map.Entry)
java.util.Date

# Field or enum constant
java.lang.System#out

# Method - parameter types are required, comma-separated, fully qualified;
# primitives are bare keywords, arrays use a trailing []
java.lang.System#exit(int)
java.lang.String#format(java.lang.String, java.lang.Object[])

# Constructor
java.lang.Integer#<init>(int)

# Optional custom diagnostic message: append " @ <message>" to any of the above
java.util.Date @ Use java.time instead
```

Parse errors report the offending file and line number, e.g. `forbidden-apis.txt:12: invalid class
name '1nvalid.Name'`.

**Duplicate signatures**: if the same class/field/method+overload/constructor+overload is banned
more than once, the *last* one (in file order, and across multiple comma-separated paths within a
single `Signatures=` value - bundles are loaded before signature files) wins for its custom
message; this is a supported way to override a bundled message, not an error.

**Multiple signature files**: pass a single `-XepOpt:ForbiddenApi:Signatures=fileA,fileB` with
comma-separated paths, not two separate `-XepOpt:ForbiddenApi:Signatures=` flag occurrences on the
same command line - `ErrorProneFlags` stores flags in a plain map keyed by flag name, so a second
occurrence silently *replaces* the first rather than merging with it, and `fileA`'s signatures
would never load at all. The same applies to `Bundles=`.

## Built-in bundles

Select with `-XepOpt:ForbiddenApi:Bundles=name1,name2`. Source: `src/main/resources/forbidden-api/*.txt`.

| Bundle | Forbids |
| --- | --- |
| `jdk-system-out` | `System#out`, `System#err` |
| `jdk-default-charset` | JDK APIs that implicitly use the JVM's platform default charset instead of an explicit `Charset` (`String` byte constructors/`getBytes()`, `InputStreamReader`/`OutputStreamWriter`, `FileReader`/`FileWriter`, `Scanner`) - each verified against the real JDK 25 API via `javap`, not guessed |
| `jdk-internals` | `sun.**`, `jdk.internal.**`, `com.sun.**` - see the caveat in that bundle's own file: several `com.sun.*` subpackages (e.g. `com.sun.net.httpserver`) are actually supported public API, so this is a blunter ban than the other two |

### Adding a new bundle

1. Add `src/main/resources/forbidden-api/<name>.txt` using the signature syntax above.
2. Add `"<name>"` to `BuiltinBundles.NAMES` in `config/BuiltinBundles.java`.
3. Add a test asserting on its parsed content (see `ForbiddenApiConfigTest`) and, where the
   signatures reference real JDK/portable APIs, a `CompilationTestHelper` test exercising it live
   (see `ForbiddenApiCheckerTest`).

## Examples

All of these are detected, matched purely on resolved symbols regardless of how the code spells
the reference:

```java
import com.foo.shaded.Bar;                    // forbidden: the import is itself a usage site

Bar value;                                     // forbidden: short name, resolved via the import
com.foo.shaded.Bar value;                      // forbidden: fully qualified
List<com.foo.shaded.Bar> values;               // forbidden: generic type argument
class X extends com.foo.shaded.Bar {}          // forbidden: superclass
class X implements com.foo.shaded.SomeIface {} // forbidden: implemented interface
@com.foo.shaded.SomeAnnotation class X {}      // forbidden: annotation
void m() throws com.foo.shaded.SomeException {}// forbidden: thrown exception type

System.exit(1);                                // forbidden if java.lang.System#exit(int) is banned
System.out.println("hi");                      // forbidden if java.lang.System#out is banned
new Integer(1);                                // forbidden if java.lang.Integer#<init>(int) is banned
Runnable r = System::exit;                     // forbidden: method reference
IntFunction<Integer> f = Integer::new;          // forbidden: constructor reference
```

Each usage site is reported exactly once, even when a single expression could theoretically touch
multiple matcher paths (e.g. `new Bar()` for a class-level ban only fires once, not once for the
type reference and once for the constructor call).

## Limitations

* **`Charset`-detection is a fixed, hand-picked list**, not general dataflow analysis - it won't
  catch e.g. a custom wrapper method that itself calls a default-charset API internally.
* **No suggested fixes.** Deliberately: renaming `System.out.println(...)` to some configured
  logger call, or swapping a `Charset`-less constructor for one that takes a `Charset`, isn't a
  mechanical rewrite that's safe to apply unattended in general. The architecture (a `Optional
  message()` per `ForbiddenSignature`, a single `report()` choke point in `ForbiddenApiChecker`)
  leaves room to attach a `SuggestedFix` per signature later, for the subset of bans where one
  really is safe and unambiguous.
* **Parameter-type text must match exactly** what the checker derives from the resolved, *erased*
  type (fully qualified name; primitives as bare keywords; arrays as trailing `[]`; varargs also as
  trailing `[]`, since that's how javac represents a varargs parameter's declared type). Generic
  type arguments in a parameter position aren't supported in the signature text (matching is by
  erasure regardless).
* **Signature files are read from a filesystem path**, resolved by the JVM's own file APIs at
  `javac` compile time - there's no support for resolving them from a Maven-repository coordinate
  or a URL.

## How this differs from import-only tools (e.g. Checkstyle's `IllegalImport`)

Checkstyle-style import bans work on source text: they see `import com.foo.Bar;` and flag it. They
generally can't reliably distinguish `com.foo.Bar` from `com.foo.impl.Bar` referenced without an
import (fully qualified inline), don't resolve inherited members, and don't see synthesized usages
like method/constructor references. `ForbiddenApi` runs after semantic analysis, seeing the same
resolved symbols the compiler itself uses for overload resolution and inheritance - so a ban on a
method holds for calls through a subclass reference too, and a package ban can't be fooled by a
class that merely starts with the same characters (`com.foo.internal.**` correctly does not match
`com.foo.internalized.SomeClass`; see `ForbiddenApiMatcherTest` and
`ForbiddenApiCheckerTest#similarlyNamedSiblingPackageIsNotMatched`).

## Comparison with `policeman-tools/forbidden-apis`

The [original `forbidden-apis`](https://github.com/policeman-tools/forbidden-apis) tool is more
mature and full-featured than this project claims to be - this section is about the *mechanism*,
not a claim of feature parity.

| | `forbidden-apis` | this project |
| --- | --- | --- |
| Analyzes | compiled `.class` bytecode, as a separate build step after compilation | source, during `javac`/Error Prone compilation itself, via resolved symbols |
| Feedback point | a dedicated Maven/Gradle/Ant goal, after `compile` | compiler errors, inline with normal `javac` diagnostics, at the exact usage site |
| Signature syntax | its own DSL, richer than this project's (e.g. `@defaultMessage`, method-signature-less "any overload" bans, bundled OWASP/JDK-deprecation signature sets) | the subset described above; no "any overload" wildcard, no bundled security-advisory signature sets |
| Runs without compiling | yes - can scan third-party jars you don't build | no - only sees your own project's compilation |

If you need to vet third-party dependency jars, or want the richer signature DSL and larger bundled
signature sets, `forbidden-apis` remains the better fit; this project is for enforcing project-local
API bans as part of the same compiler pass that already runs Error Prone/NullAway.

## Building

```shell
./mvnw verify
```

Test coverage is enforced at 100% (line and branch) via JaCoCo; `verify` fails if it drops below that. Run
`open target/site/jacoco/index.html` after a build to see the report.

`verify` also runs Spotless (palantir-java-format + sorted `pom.xml`), Checkstyle, and Error Prone/NullAway via
the compiler plugin. Run `./mvnw spotless:apply` to auto-format before committing.


## Mutation testing

[![Mutation Score](https://haisi.github.io/error-prone-support/pit/badge.svg)](https://haisi.github.io/error-prone-support/pit/)

Line/branch coverage only proves a test executed some code, not that it would notice a bug in it. [PIT
mutation testing](https://pitest.org) seeds small deliberate bugs ("mutants") into the compiled classes and
checks whether the test suite actually fails for each one; a mutant that survives is a gap in the tests.

Mutation testing runs nightly at around 02:00 UTC via `.github/workflows/pit-mutation-testing.yml`, and only
when at least one new commit has landed on `main` since the last successful run - so it stays off the critical
path for every push/PR while still picking up changes automatically. It can also be triggered manually from
the Actions tab.

See the full HTML mutation report at `https://haisi.github.io/error-prone-support/pit/` for a per-class,
per-mutator breakdown, or run it locally with:

```shell
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
open target/pit-reports/index.html
```


## Releasing

Releases are published to Maven Central via [JReleaser](https://jreleaser.org). Pushing a tag matching `v*`
(e.g. `v1.0.0`) triggers `.github/workflows/release.yml`, which stages the build artifacts and hands them to
JReleaser to sign and deploy to the [Central Portal](https://central.sonatype.com).

```shell
./bumpPomVersion.sh
git push
./release.sh
```


Bug reports, feature requests and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). This
project follows a [Code of Conduct](CODE_OF_CONDUCT.md); by participating you agree to abide by it.


`bugpatterns` is licensed under the [Apache License, Version 2.0](LICENSE).

See `jreleaser.yml` for the deployment configuration.
