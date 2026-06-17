pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "eventbus-nflow-poc"

val useLocalKsbCommons =
    providers.gradleProperty("useLocalKsbCommons")
        .map(String::toBoolean)
        .orElse(true)
        .get()

val ksbCommonsDir = file("vendor/ksb-commons")

if (useLocalKsbCommons) {
    if (ksbCommonsDir.exists()) {
        includeBuild(ksbCommonsDir) {
            dependencySubstitution {
                substitute(module("io.jrb.labs:ksb-commons-core"))
                    .using(project(":ksb-commons-core"))
                substitute(module("io.jrb.labs:ksb-commons-ms-client"))
                    .using(project(":ksb-commons-ms-client"))
                substitute(module("io.jrb.labs:ksb-commons-ms-core"))
                    .using(project(":ksb-commons-ms-core"))
                substitute(module("io.jrb.labs:ksb-commons-test"))
                    .using(project(":ksb-commons-test"))
                substitute(module("io.jrb.labs:ksb-spring-boot-starter-reactive"))
                    .using(project(":ksb-spring-boot-starter-reactive"))
                substitute(module("io.jrb.labs:ksb-spring-boot-starter-reactive-test"))
                    .using(project(":ksb-spring-boot-starter-reactive-test"))
            }
        }
    } else {
        logger.warn("useLocalKsbCommons=true but ./vendor/ksb-commons is missing; using published dependencies")
    }
}
