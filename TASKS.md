# Task plan — error-prone-support / ForbiddenApi checker

Tracks execution of the plan in `prompt.md`. Update statuses as work progresses.
States: `open`, `in_progress`, `done`.

```text
[done]        2026-08-16 Set up Maven module via java-lib-archetype, verify build with JDK 25
[in_progress] 2026-08-16 Architecture review (Java Library Expert agent) of proposed design
[open]        2026-08-16 Add error_prone_check_api / error_prone_test_helpers dependencies
[open]        2026-08-16 Define internal model (ForbiddenSignature sealed hierarchy)
[open]        2026-08-16 Implement ForbiddenSignatureParser (signature DSL + parse errors)
[open]        2026-08-16 Implement ForbiddenApiMatcher (semantic symbol matching, package glob)
[open]        2026-08-16 Implement bundle loading mechanism (Bundles=... option)
[open]        2026-08-16 Author built-in bundles: jdk-system-out, jdk-default-charset, jdk-internals
[open]        2026-08-16 Implement ForbiddenApiChecker (BugChecker, ErrorProneFlags, diagnostics)
[open]        2026-08-16 Register checker as Error Prone plugin (META-INF/services)
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

## Notes

- JDK: build with Temurin 25 (`~/Library/Java/JavaVirtualMachines/temurin-25.0.3`), NOT the
  system default JDK 26 (Homebrew) — Error Prone 2.50.0 compatibility with JDK 26 is unverified;
  25 is the archetype's own default and known-good.
- groupId=`li.selman.error-prone`, artifactId=`bugpatterns`, package=`li.selman.errorprone.bugpatterns`.
- GitHub repo: `haisi/error-prone-support` (created private; pushed after initial scaffold commit).
- Archetype's `qa` Maven profile enforces: Spotless (palantir-java-format), Checkstyle, NullAway
  (`-Werror`), and 100% line+branch JaCoCo coverage on `verify`. All new code must satisfy this.
