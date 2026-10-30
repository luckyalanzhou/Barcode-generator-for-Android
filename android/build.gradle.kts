plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register("verifyArchitectureModuleDependencies") {
    group = "verification"
    description = "Verifies production Gradle module dependencies and architecture boundaries."

    // 在任务配置阶段读取 Gradle 模型；任务动作只捕获不可变数据，兼容 configuration cache。
    val expectedDependencies = mapOf(
        ":app" to setOf(":core:domain", ":core:data", ":core:lan-share"),
        ":core:domain" to emptySet(),
        ":core:data" to setOf(":core:domain"),
        ":core:lan-share" to setOf(":core:domain"),
    )
    val actualModules = subprojects
        .filter { it.buildFile.isFile && it.path != ":architecture-tests" }
        .map { it.path }
        .toSet()
    val actualDependencies = expectedDependencies.keys.associateWith { modulePath ->
        project(modulePath).configurations
            .flatMap { configuration ->
                configuration.dependencies.withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                    .map { dependency -> dependency.path }
            }
            .filterNot { it == modulePath }
            .toSet()
    }
    val forbiddenPrefixes = listOf("androidx.", "com.android.", "com.google.zxing", "com.google.dagger")
    val domainProductionConfigurations = listOf("api", "implementation", "compileOnly", "runtimeOnly")
    val forbiddenDependencies = domainProductionConfigurations
        .flatMap { configurationName ->
            project(":core:domain").configurations.getByName(configurationName).dependencies
                .withType(org.gradle.api.artifacts.ExternalModuleDependency::class.java)
        }
        .mapNotNull { dependency ->
            dependency.group?.takeIf { group -> forbiddenPrefixes.any(group::startsWith) }
                ?.let { group -> "$group:${dependency.name}" }
        }
        .distinct()

    doLast {
        check(actualModules == expectedDependencies.keys) {
            "Production modules changed. Update the expected module list and architecture tests. " +
                "Expected=${expectedDependencies.keys}, actual=$actualModules"
        }

        expectedDependencies.forEach { (modulePath, expectedModuleDependencies) ->
            check(actualDependencies[modulePath] == expectedModuleDependencies) {
                "$modulePath has unexpected project dependencies. " +
                    "Expected=$expectedModuleDependencies, actual=${actualDependencies[modulePath]}"
            }
        }

        check(forbiddenDependencies.isEmpty()) {
            ":core:domain must not depend on Android, Compose, ZXing, or DI implementations. " +
                "Found=$forbiddenDependencies"
        }
    }
}

/** 单一本地验收入口；不打包、不发布，也不改变远端手动构建任务。 */
tasks.register("verifyLocal") {
    group = "verification"
    description = "Runs complete architecture, core/app tests and both release channel checks without packaging."
    if (providers.gradleProperty("enableAppUnitTests").map(String::toBoolean).getOrElse(false)) {
        dependsOn(":architecture-tests:test", "verifyArchitectureModuleDependencies",
            ":core:domain:test", ":core:data:testDebugUnitTest", ":core:lan-share:testDebugUnitTest",
            ":app:testBetaDebugUnitTest", ":app:compileBetaReleaseKotlin", ":app:compileOfficialReleaseKotlin",
            ":app:lintBetaRelease", ":app:lintOfficialRelease")
    } else doFirst { error("verifyLocal requires -PenableAppUnitTests=true; it does not enable or publish debug APKs automatically.") }
}
