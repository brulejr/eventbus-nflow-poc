# Git submodule notes

This repository can use `ksb-commons` from `vendor/ksb-commons` as a local Gradle included build.
That keeps shared commons code editable while this POC is being developed.

## Add the submodule

Create the vendor directory if it does not exist:

```bash
mkdir -p vendor
```

Add the submodule:

```bash
git submodule add git@github.com:brulejr/ksb-commons.git vendor/ksb-commons
```

For a fresh checkout that already has the submodule recorded:

```bash
git submodule update --init --recursive
```

## Gradle settings

`settings.gradle.kts` should include the local build when `vendor/ksb-commons` exists:

```kotlin
val useLocalKsbCommons =
    providers.gradleProperty("useLocalKsbCommons")
        .map { it.toBoolean() }
        .orElse(true)
        .get()

val ksbCommonsDir = file("vendor/ksb-commons")

if (useLocalKsbCommons) {
    if (ksbCommonsDir.exists()) {
        includeBuild(ksbCommonsDir)
    } else {
        logger.warn("useLocalKsbCommons=true but ./vendor/ksb-commons is missing; falling back to published dependency")
    }
}
```

`gradle.properties` controls whether the local included build or a published dependency is used:

```properties
useLocalKsbCommons=true
ksbCommonsVersion=0.4.2
```

## Build dependency pattern

The root `build.gradle.kts` reads `ksbCommonsVersion` and declares the commons modules directly:

```kotlin
val ksbCommonsVersion: String by project

dependencies {
    implementation("io.jrb.labs:ksb-commons-core:$ksbCommonsVersion")
    implementation("io.jrb.labs:ksb-commons-ms-core:$ksbCommonsVersion")
}
```

When `useLocalKsbCommons=true`, Gradle resolves those coordinates from the included build. When it is
false, Gradle resolves the published artifacts.

## Published artifact access

If using published GitHub Packages artifacts, configure the GitHub Packages repository and credentials
in `build.gradle.kts`:

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/brulejr/ksb-commons")
    credentials {
        username = findProperty("gpr.user") as String?
            ?: System.getenv("GITHUB_PACKAGES_USER")
            ?: System.getenv("GITHUB_ACTOR")
            ?: "brulejr"
        password = findProperty("gpr.key") as String?
            ?: System.getenv("GITHUB_PACKAGES_TOKEN")
            ?: System.getenv("GITHUB_TOKEN")
    }
}
```

## Common commands

Update the submodule to the commit recorded by this repository:

```bash
git submodule update --init --recursive
```

Pull the latest submodule commit from its tracked branch:

```bash
git submodule update --remote vendor/ksb-commons
```

Show submodule status:

```bash
git submodule status
```

## Resources

- [Git submodule documentation](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
- [Git submodules: adding, using, removing, updating](https://chrisjean.com/git-submodules-adding-using-removing-and-updating/)
