# Overview
The **ksb-commons** library is connected to this repository as a Git submodule.

# Setup

Create the vendor directory.
```bash
mkdir vendor
```

Initialize and update the submodule.
```bash
git submodule add git@github.com:brulejr/ksb-commons.git vendor/ksb-commons
```

Add the following bock to `settings.gradle` to include the submodule in the Gradle build.
```groovy
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

Add the following to `gradle.properties` to use the local submodule by default.
```properties
# Version of shared commons BOM
useLocalKsbCommons=true
ksbCommonsVersion=0.4.2
```

Within `build.gradle.kts`, add the following to the top of the file to use the submodule.
```kotlin
val ksbCommonsVersion: String by project
val useLocalKsbCommons: Boolean =
    providers.gradleProperty("useLocalKsbCommons")
        .map { it.toBoolean() }
        .orElse(true)
        .get()
```
Add the following to the repositories block to use the submodule.
```kotlin
    if (!useLocalKsbCommons) {
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
    }
```
Add the following to the dependencies block to use the submodule.
```kotlin
    if (useLocalKsbCommons) {
        implementation(platform("io.jrb.labs:ksb-dependency-bom"))
    } else {
        implementation(platform("io.jrb.labs:ksb-dependency-bom:$ksbCommonsVersion"))
    }
```

# Resources
- [Git Submodules: Adding, Using, Removing, Updating](https://chrisjean.com/git-submodules-adding-using-removing-and-updating/)
- [Git submodule documentation](https://git-scm.com/book/en/v2/Git-Tools-Submodules)