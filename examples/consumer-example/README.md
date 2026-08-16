# consumer-example

A minimal, standalone Maven project showing how to consume the `ForbiddenApi` Error Prone checker
from another project. It is **not** part of this repository's own reactor build — it has its own
`pom.xml` with no parent, and is meant to be copied out or read as a reference.

## Try it

```shell
# From the repository root: publish the checker to your local ~/.m2 first.
cd ../..
./mvnw install -Dquick -DskipTests
cd examples/consumer-example

# Compiles cleanly - nothing in Demo.java trips config/forbidden-apis.txt.
mvn compile

# Now introduce a violation and watch it fail:
#   echo 'java.util.Date d;' would need to go inside a class body - e.g. add a field
#   `java.util.Date field;` to Demo.java - and re-run `mvn compile`. You should see:
#
#   [ERROR] .../Demo.java:[8,5] [ForbiddenApi] java.util.Date is forbidden. Use java.time instead
```

`config/forbidden-apis.txt` forbids `java.util.Date`, `java.lang.System#out`, and the (here
nonexistent, just illustrative) `com.example.shaded.**` package - see the main README for the full
signature syntax.

**`.mvn/jvm.config`**: this directory carries its own copy (the standard set required to run any
Error Prone plugin on JDK 16+) rather than relying on any ancestor directory's - Maven discovers
`.mvn/jvm.config` by walking up from the current directory regardless of reactor/module membership,
which initially masked a real bug here: this example was first validated only from inside the
parent repository (which has its own, fuller `.mvn/jvm.config`), so it silently inherited flags a
genuinely separate project wouldn't have. Re-verified from a directory with no such ancestor - see
`TASKS.md` in the repository root for the full story.
