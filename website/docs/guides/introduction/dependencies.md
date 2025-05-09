---
title: Managing dependencies
sidebar_position: 3
---

import {ChainedSnippets} from "../../../src/components/MarkdownComponents.js";

# Managing dependencies

## Dependency syntax

Dependencies are declared in Scala CLI according to the following format:

```text
groupID:artifactID:revision
```

This is similar to how you declare dependencies in SBT with the `%` character.
For example:

```text
org.scala-lang.modules:scala-parallel-collections_2.13:1.0.4
```

You can also skip explicitly stating the Scala version in the artifact name by repeating the `:` character after
the `groupID` (similarly to how you can do the same with `%%` in SBT). This is just a shortcut, Scala CLI will still add
the Scala version for you when fetching the dependency. Also, this only applies to Scala dependencies.

```text
org.scala-lang.modules::scala-parallel-collections:1.0.4
```

Java and other non-scala dependencies follow the same syntax (without the `::` for implicit Scala version, of course).
For example:

```text
org.postgresql:postgresql:42.2.8
```

### Repositories

Sometimes dependencies are published into non-standard repositories, like nightly builds published to Sonatype Snapshots. Scala CLI can use additional maven and ivy repositories with the `repository` directive or `--repository` command line options:

```scala
//> using repository sonatype:snapshots
```

or

```bash ignore
scala-cli --repository "https://maven-central.storage-download.googleapis.com/maven2"
```



Both directive and command line option accept predefined repository definitions (see below) or a URL of the root of Maven repository.

Repositories can also be resolved from the `COURSIER_REPOSITORIES` environment variable, but this is not recommended (more in [Coursier documentation](https://get-coursier.io/docs/other-repositories)).


#### Predefined repositories

| predefined repository  | kind                                                                                                                                                        | description                                                                                                                                                                           |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| central                | Maven [(root)](https://repo1.maven.org/maven2)                                                                                                              | Used by default, default repository for most Scala libraries                                                                                                                          |
| sonatype:snapshots     | Maven [(root)](https://oss.sonatype.org/content/repositories/snapshots)                                                                                     | Repositories where most Scala libraries publish its snapshots / nightly builds. Used when `X.nightly` is used as Scala version e.g. `3.1.nightly`.                                    |
| sonatype-s01:snapshots | Maven [(root)](https://s01.oss.sonatype.org/content/repositories/snapshots)                                                                                 | This repository is similar to the `sonatype:snapshots` repository but is dedicated for accounts that were created after February 2021 and which publish snapshots of their libraries. |
| snapshots              | Maven [(root)](https://oss.sonatype.org/content/repositories/snapshots) and Maven S01 [(root)](https://s01.oss.sonatype.org/content/repositories/snapshots) | An alias for `sonatype:snapshots` and `sonatype-s01:snapshots`.                                                                                                                      
| ivy2local              | Ivy                                                                                                                                                         | Local ivy repository, used to publish things locally (e.g. by `publishLocal`). Localized in `<ivy-home>/local`, usually `<user-home>/.ivy/local`.                                     |
| m2Local                | Maven                                                                                                                                                       | Local maven repository, localized in `<user-home>/.m2/repository`                                                                                                                     |
| jitpack | Maven | jitpack supports github repo as dependency. Syntax is `using repository "jitpack"`

Scala CLI delegates parsing of predefined repositories to Coursier and full details can be obtained from Coursier source code ([here](https://github.com/coursier/coursier/blob/2444eebcc151e0f6927e269137e8737c1f31cbe2/modules/coursier/jvm/src/main/scala/coursier/LocalRepositories.scala) and [here](https://github.com/coursier/coursier/blob/2444eebcc151e0f6927e269137e8737c1f31cbe2/modules/coursier/shared/src/main/scala/coursier/internal/SharedRepositoryParser.scala))

### Excluding Transitive Dependencies

To exclude a transitive dependency from a Scala CLI project use the `exclude` parameter:

- `exclude=org%%name` - for Scala modules
- `exclude=org%name` - for Java modules

It requires passing the organization and module name of the dependency to be excluded. For example, let's say you have
the following Scala code:

```scala compile
//> using dep com.lihaoyi::pprint:0.9.0
object Main extends App {
  println("Hello")
}
```

If you want to compile it with the `pprint` library but exclude its `sourcecode` dependency, you can use
the `exclude` parameter as follows:

```scala compile
//> using dep com.lihaoyi::pprint:0.9.0,exclude=com.lihaoyi%%sourcecode
object Main extends App {
  println("Hello")
}
```

To exclude Scala modules, you can also use a single `%` but with the full name of the module name, like this:

```scala compile
//> using dep com.lihaoyi::pprint:0.9.0,exclude=com.lihaoyi%sourcecode_3
object Main extends App {
  println("Hello")
}
```

### Dependency classifiers

To specify a classifier of a dependency in a Scala CLI project, use the `classifier` parameter:

- `classifier={classifier_name}`

If you want to use the `pytorch` dependency with the classifier `linux-x86_64`, use the `classifier` parameter as
follows:

```scala compile
//> using dep org.bytedeco:pytorch:2.5.1-1.5.11,classifier=linux-x86_64
object Main extends App {
  println("Hello")
}
```

:::caution
When using the `classifier`, `exclude` or others parameters, it is necessary to wrap the value of dependency within double quotes `"`.
If this is omitted, Scala CLI treats these parameters as dependencies, resulting in a dependency parsing error.
:::

### Test dependencies

It is possible to declare dependencies limited to the test scope with the `using test.dep` directive.

```scala compile
//> using test.dep org.scalameta::munit::1.0.2
```

More details can be found in
the [`using` directives guide](using-directives.md#directives-with-a-test-scope-equivalent).

### Compile-Only dependencies

It is possible to declare dependencies that are only used during the compilation phase with the `using compileOnly.dep` directive.

```scala compile
//> using compileOnly.dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.23.2
```

More details can be found in
the [`compile` command guide](../../commands/compile.md#compile-only-dependencies).

## Specifying dependencies from the command line

You can add dependencies on the command line, with the `--dependency` option:

```scala title=Sample.sc
println("Hello")
```

```bash
scala-cli compile Sample.sc \
  --dependency org.scala-lang.modules::scala-parallel-collections:1.0.4
```

You can also add a URL fallback for a JAR dependency, if it can't be fetched otherwise:

```bash ignore
scala-cli compile Sample.sc \
  --dependency "org::name::version,url=https://url-to-the-jar"
```

Note that `--dependency` is only meant as a convenience. You should favor adding dependencies in the sources themselves
via [using directives](configuration.md#special-imports). However, the `--dependency` CLI option takes
precedence over `using` directives, so it can be used to override a `using` directive, such as when you want to work
with a different dependency version.

You can also add repositories on the command-line, via `--repository` or `//> using repos`

```bash ignore
scala-cli compile Sample.sc \
  --dependency com.pany::util:33.1.0 --repo https://artifacts.pany.com/maven
```

Lastly, you can also add simple JAR files as dependencies with `--jar`:

```bash ignore
scala-cli compile Sample.sc --jar /path/to/library.jar
```

## Adding local JARs as dependencies
You can pass local JARs from the command line with the `--extra-jar` option:

```bash ignore
scala-cli compile Sample.sc \
  --extra-jar "./path/to/custom.jar"
```

Local sources JARs can also be passed in a similar manner:
```bash ignore
scala-cli compile Sample.sc \
  --extra-source-jar "./path/to/custom-sources.jar"
```

Both can be handled with the appropriate `using` directives, too:

```scala
//> using jar ./path/to/custom.jar
//> using sourceJar ./path/to/custom-sources.jar
```

:::caution
Local JARs with the `*-sources.jar` suffix are assumed to be sources JARs and are treated as such.
:::
