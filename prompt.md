I want to create a new java library called "error-prone-support"
Artifact and GroupId: li.selman.error-prone:bugpatterns
You must use my maven archetype to scaffold the project: https://github.com/haisi/java-lib-archetype

The goal of the library is to give me a place to developt custom Error-Prone bug patterns (https://github.com/google/error-prone)

Inspirations are:
- https://github.com/palantir/baseline-error-prone
- https://github.com/PicnicSupermarket/error-prone-support

In the future I also add refaster rules

Here follows the prompt I created with an LLM.


## Execution protocol

This is a large, multi-part engineering task. Do not treat it as a single implementation step.

Before making code changes, create a task plan and split the work into concrete, independently verifiable tasks. Maintain that plan throughout the work.

Each task must have one of these states:

```text
open
in_progress
done
```

Each task must also include a date in `YYYY-MM-DD` format.

Example:

```text
[open] 2026-08-16 Define signature DSL and parser grammar
[in_progress] 2026-08-16 Implement semantic symbol matching
[done] 2026-08-16 Set up Maven module and Error Prone test harness
```

Update the task list as work progresses. Do not mark a task `done` until its implementation and relevant tests are complete.

Work incrementally. Prefer completing and validating one coherent slice before moving to the next.

## Use sub-agents

Actively delegate review and verification work to specialized sub-agents where the environment supports it.

Create at least these independent roles:

### Java Library Expert / Architect

Use this sub-agent early, before or during implementation.

Its responsibilities are to:

* review the proposed project structure;
* evaluate the public configuration model and signature DSL;
* verify that the Error Prone integration uses appropriate extension APIs;
* identify unnecessary coupling to javac internals;
* review extensibility for future signature types and bundled policies;
* challenge architectural decisions that may make the library difficult to maintain;
* review Maven consumption and packaging design.

This agent should focus on design quality, Java library ergonomics, Error Prone conventions, compatibility, and maintainability.

### Code Review

Use this sub-agent after substantial implementation is complete.

Its responsibilities are to perform an independent code review looking for:

* correctness bugs;
* symbol-resolution mistakes;
* duplicate or missing diagnostics;
* incorrect handling of overloaded methods and constructors;
* package/glob boundary bugs;
* Error Prone lifecycle or state-management issues;
* thread-safety concerns;
* unnecessary complexity;
* poor API naming;
* resource leaks;
* brittle assumptions about source layout;
* misuse of Error Prone or javac APIs.

The code-review agent should not merely summarize the implementation. It should actively try to find defects.

Any issues it finds must be added back to the main task list as `open` tasks and resolved before the project is considered complete.

### QA / Test Engineer

Use this sub-agent independently from Code Review.

Its responsibilities are to:

* review the requirements against the implemented behavior;
* identify missing test cases;
* design adversarial and boundary tests;
* verify positive and negative matches;
* test similarly named packages/classes;
* test overload resolution;
* test constructors, fields, nested classes, annotations, generics, inheritance, and fully-qualified references;
* test malformed configuration;
* test duplicate rules;
* test bundled signature loading;
* test custom messages;
* test Maven consumption from a separate sample project;
* verify that expected compilation failures actually fail for the right reason.

The QA agent should assume the implementation may be wrong and should attempt to break it.

Any gaps or failures must be added to the main task list and resolved.

## Independent verification

Do not allow the implementing agent to be the only source of validation.

At minimum, complete this sequence:

```text
architecture review
implementation
automated tests
code review
QA review
fix findings
rerun automated tests
final verification
```

If a reviewer finds an issue, do not simply explain it away. Either fix it or explicitly document why the finding is incorrect, with evidence.

## Progress discipline

Keep the task plan current throughout the work.

A reasonable initial breakdown should include areas such as:

```text
project setup
Error Prone plugin registration
configuration model
signature DSL design
signature parser
semantic matcher model
class matching
package matching
field matching
method matching
constructor matching
diagnostic generation
bundle loading
built-in bundles
Error Prone integration tests
parser unit tests
boundary/adversarial tests
Maven consumer example
documentation
architecture review
code review
QA review
final verification
```

Refine or split these tasks further where appropriate.

Only one or a small number of closely related tasks should normally be `in_progress` at once.

## Definition of done

Do not consider the task complete merely because the code compiles.

The project is complete only when:

* all planned tasks are `done`;
* the full automated test suite passes;
* the Code Review sub-agent has completed its review;
* the QA sub-agent has completed its review;
* the Java Library Expert / Architect has reviewed the design;
* findings from those reviews have been addressed;
* the Maven consumer example has been validated;
* documentation reflects the actual implemented behavior;
* limitations and unsupported cases are documented;
* the final report includes the completed task list with dates.

If sub-agents are not technically available in the environment, emulate the same separation of concerns by performing distinct architecture-review, code-review, and QA passes, and clearly label those passes. Do not skip them.



Build a reusable Java library that provides a configurable Error Prone `BugChecker` for forbidding APIs using signature files inspired by `policeman-tools/forbidden-apis`.

The goal is to provide compile-time, type-aware enforcement with good Error Prone diagnostics, while keeping configuration simple and externalized.

## Requirements

Create a Maven-based Java project containing an Error Prone checker named:

`ForbiddenApi`

The checker must support configuration through Error Prone options, for example:

```text
-Xep:ForbiddenApi:ERROR
-XepOpt:ForbiddenApi:Signatures=config/forbidden-apis.txt
```

Optionally also support bundled signature sets:

```text
-XepOpt:ForbiddenApi:Bundles=jdk-default-charset,jdk-system-out,jdk-internals
```

Design the code so additional bundles can easily be added later.

## Signature file format

Use a simple line-oriented DSL inspired by `forbidden-apis`.

Support at least:

```text
# Package / namespace
com.mycompany.shaded.**

# Exact class
java.util.Date

# Field
java.lang.System#out

# Method
java.lang.System#exit(int)

# Constructor
java.lang.Integer#<init>(int)
```

Support an optional custom diagnostic message:

```text
java.util.Date @ Use java.time instead

java.lang.System#out @ Use the configured logger instead

com.mycompany.shaded.** @ Shaded dependencies are implementation details
```

Ignore:

* blank lines
* lines beginning with `#`

Produce useful parse errors including filename and line number.

## Matching semantics

The checker must operate on resolved javac/Error Prone symbols rather than source text wherever possible.

It must catch forbidden usages regardless of whether they are imported or fully qualified.

Examples that should all be detected for a forbidden class/package:

```java
import com.foo.shaded.Bar;

Bar value;

com.foo.shaded.Bar value;

List<com.foo.shaded.Bar> values;

class X extends com.foo.shaded.Bar {}

@com.foo.shaded.Annotation
class X {}
```

It should also detect forbidden:

* method invocations
* static method invocations
* constructors
* field accesses
* static fields
* enum constants
* types in generic arguments
* annotations
* superclass/interface references
* method parameter types
* return types
* field types
* thrown exception types where applicable

Avoid reporting the same source usage multiple times.

## Package glob semantics

Support:

```text
com.foo.**
```

meaning the package itself and all descendants.

If practical, also support:

```text
com.foo.*
```

for direct children only.

Keep the matcher implementation explicit and well-tested rather than relying on fragile string-prefix checks.

## Internal model

Do not bake parsing logic directly into the BugChecker.

Create a small internal model, for example:

```text
ForbiddenSignature
  PackageSignature
  ClassSignature
  MethodSignature
  ConstructorSignature
  FieldSignature
```

Use immutable value objects.

Have a separate parser, matcher, and Error Prone integration layer.

Suggested structure:

```text
src/main/java/.../ForbiddenApiChecker.java
src/main/java/.../config/ForbiddenSignatureParser.java
src/main/java/.../model/ForbiddenSignature.java
src/main/java/.../model/PackageSignature.java
src/main/java/.../model/ClassSignature.java
src/main/java/.../model/MethodSignature.java
src/main/java/.../model/FieldSignature.java
src/main/java/.../matcher/ForbiddenApiMatcher.java
```

Feel free to improve the structure if there is a cleaner design.

## Error Prone integration

Register the checker correctly so it can be discovered as a custom Error Prone plugin.

Use the appropriate Error Prone APIs for the current stable version.

Use `ErrorProneFlags` for configuration.

The checker should have:

```java
@BugPattern(
    name = "ForbiddenApi",
    summary = "Usage of a forbidden API",
    severity = ERROR
)
```

Use precise diagnostics.

For example:

```text
ForbiddenApi: java.util.Date is forbidden. Use java.time instead.
```

or:

```text
ForbiddenApi: java.lang.System#out is forbidden. Use the configured logger instead.
```

If there is no custom message, provide a sensible default.

## Built-in bundles

Implement an extensible mechanism for bundled signatures.

Initially add these bundles:

### `jdk-system-out`

Forbid:

```text
java.lang.System#out
java.lang.System#err
```

### `jdk-default-charset`

Cover common JDK APIs that implicitly use the platform default charset.

Include appropriate signatures such as common zero-charset-argument variants of:

```text
java.lang.String
java.lang.String#getBytes()
java.io.InputStreamReader
java.io.OutputStreamWriter
java.io.FileReader
java.io.FileWriter
java.util.Scanner
```

Verify signatures against the actual JDK APIs instead of guessing.

### `jdk-internals`

Forbid obvious internal JDK namespaces such as:

```text
sun.**
com.sun.**
jdk.internal.**
```

Be careful with `com.sun.*`: document any exceptions or portability implications.

Keep bundle contents in resource files rather than hardcoding them in Java where practical.

For example:

```text
src/main/resources/forbidden-api/jdk-system-out.txt
src/main/resources/forbidden-api/jdk-default-charset.txt
src/main/resources/forbidden-api/jdk-internals.txt
```

## Suggested fixes

Do not attempt unsafe automatic rewrites.

However, structure the checker so individual signatures or future specialized checks could attach `SuggestedFix` instances later.

If there are trivial, unquestionably safe fixes, they may be added, but correctness is more important than having fixes.

## Maven support

Provide a complete example Maven configuration showing how another project consumes the checker.

For example, demonstrate:

```xml
<annotationProcessorPaths>
    <path>
        <groupId>com.google.errorprone</groupId>
        <artifactId>error_prone_core</artifactId>
        <version>...</version>
    </path>

    <path>
        <groupId>...</groupId>
        <artifactId>forbidden-api-error-prone</artifactId>
        <version>...</version>
    </path>
</annotationProcessorPaths>
```

and compiler args similar to:

```xml
<compilerArgs>
    <arg>-Xplugin:ErrorProne</arg>
    <arg>-Xep:ForbiddenApi:ERROR</arg>
    <arg>-XepOpt:ForbiddenApi:Signatures=${project.basedir}/config/forbidden-apis.txt</arg>
</compilerArgs>
```

Make sure the Maven example is actually valid for the Error Prone version selected.

## Tests

Use Error Prone's testing utilities, preferably `CompilationTestHelper`.

Write thorough tests covering:

* forbidden exact class
* allowed class
* forbidden package
* nested package
* fully qualified reference
* imported reference
* field access
* static field access
* method call
* static method call
* overloaded methods
* constructor
* generic type argument
* annotation
* superclass
* implemented interface
* custom diagnostic message
* comments and blank lines in config
* malformed signature
* duplicate signatures
* multiple configured signature files if supported
* built-in bundles
* no duplicate diagnostics for one usage

Also test that similarly named classes/packages do not accidentally match.

For example:

```text
com.foo.internal.**
```

must not match:

```text
com.foo.internalized.SomeClass
```

## Documentation

Write a README covering:

1. What the project does
2. Installation
3. Maven configuration
4. Signature syntax
5. Built-in bundles
6. Examples
7. Limitations
8. How to add a new bundle
9. How the semantic matching differs from import-only tools such as Checkstyle
10. Comparison with `policeman-tools/forbidden-apis`

Clarify that the major difference is:

* `forbidden-apis` primarily analyzes compiled bytecode
* this project performs enforcement during javac/Error Prone compilation using resolved symbols

Do not claim feature parity unless it actually exists.

## Engineering expectations

Prefer simple, maintainable code over clever abstractions.

Use modern Java supported by the selected Error Prone version.

Avoid unnecessary dependencies.

Follow Error Prone's intended extension APIs rather than depending on unstable javac internals when an Error Prone helper exists.

Before implementing matcher logic, inspect the current Error Prone APIs and relevant upstream implementations.

Also inspect the signature syntax and bundled-signature concepts in:

`https://github.com/policeman-tools/forbidden-apis`

Use it only as design inspiration; do not copy source code wholesale.

At the end, provide:

* the final project structure
* the main design decisions
* any Error Prone API limitations encountered
* exact commands to build and run the tests
* a minimal consuming Maven project example
