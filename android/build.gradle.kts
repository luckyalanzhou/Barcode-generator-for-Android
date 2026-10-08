plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

val expectedArchitectureModuleDependencies = mapOf(
    ":app" to setOf(":core:domain", ":core:data", ":core:lan-share"),
    ":core:domain" to emptySet(),
    ":core:data" to setOf(":core:domain"),
    ":core:lan-share" to setOf(":core:domain"),
)

val forbiddenDomainDependencyPrefixes = listOf(
    "androidx.",
    "com.android.",
    "com.google.zxing",
    "com.google.dagger",
)

tasks.register("verifyArchitectureModuleDependencies") {
    group = "verification"
    description = "Verifies production Gradle module dependencies against ARCHITECTURE.md."

    doLast {
        val actualProductionModules = subprojects
            .filter { it.buildFile.isFile && it.path != ":architecture-tests" }
            .map { it.path }
            .toSet()
        check(actualProductionModules == expectedArchitectureModuleDependencies.keys) {
            "Production modules changed. Update ARCHITECTURE.md and the expected module list. " +
                "Expected=${expectedArchitectureModuleDependencies.keys}, actual=$actualProductionModules"
        }

        expectedArchitectureModuleDependencies.forEach { (modulePath, expectedDependencies) ->
            val actualDependencies = project(modulePath)
                .configurations
                .flatMap { configuration ->
                    configuration.dependencies.withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                        .map { dependency -> dependency.path }
                }
                .filterNot { it == modulePath }
                .toSet()

            check(actualDependencies == expectedDependencies) {
                "$modulePath has unexpected project dependencies. " +
                    "Expected=$expectedDependencies, actual=$actualDependencies"
            }
        }

        val domain = project(":core:domain")
        val domainProductionConfigurations = listOf("api", "implementation", "compileOnly", "runtimeOnly")
        val forbiddenDomainDependencies = domainProductionConfigurations
            .flatMap { configurationName ->
                domain.configurations.getByName(configurationName).dependencies
                    .withType(org.gradle.api.artifacts.ExternalModuleDependency::class.java)
            }
            .mapNotNull { dependency ->
                dependency.group?.takeIf { group -> forbiddenDomainDependencyPrefixes.any(group::startsWith) }
                    ?.let { group -> "$group:${dependency.name}" }
            }
            .distinct()

        check(forbiddenDomainDependencies.isEmpty()) {
            ":core:domain must not depend on Android, Compose, ZXing, or DI implementations. " +
                "Found=$forbiddenDomainDependencies"
        }
    }
}
