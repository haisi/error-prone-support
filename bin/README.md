# bugpatterns

[![CI](https://github.com/haisi/error-prone-support/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/haisi/error-prone-support/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/haisi/error-prone-support/badge.svg?branch=main)](https://coveralls.io/github/haisi/error-prone-support?branch=main)
[![Maven Central](https://img.shields.io/maven-central/v/li.selman.error-prone/bugpatterns.svg)](https://central.sonatype.com/artifact/li.selman.error-prone/bugpatterns)
[![Javadoc](https://javadoc.io/badge2/li.selman.error-prone/bugpatterns/javadoc.svg)](https://javadoc.io/doc/li.selman.error-prone/bugpatterns)
[![License](https://img.shields.io/github/license/haisi/error-prone-support)](LICENSE)
[![Mutation Score](https://haisi.github.io/error-prone-support/pit/badge.svg)](https://haisi.github.io/error-prone-support/pit/)

Custom Error Prone bug patterns, starting with a configurable ForbiddenApi checker for signature-based API bans.

[**Website**](https://haisi.github.io/error-prone-support/)

<!-- TODO: replace this with your library's actual usage instructions, and delete src/main/java/Placeholder.java
     and its test - they only exist so this freshly generated project builds and documents out of the box. -->


Add dependency

```xml
<dependency>
    <groupId>li.selman.error-prone</groupId>
    <artifactId>bugpatterns</artifactId>
    <version>VERSION</version>
</dependency>
```


```shell
./mvnw verify
```

Test coverage is enforced at 100% (line and branch) via JaCoCo; `verify` fails if it drops below that. Run
`open target/site/jacoco/index.html` after a build to see the report.

`verify` also runs Spotless (palantir-java-format + sorted `pom.xml`), Checkstyle, and Error Prone/NullAway via
the compiler plugin. Run `./mvnw spotless:apply` to auto-format before committing.


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
