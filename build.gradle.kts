plugins {
    kotlin("jvm") version "1.4.21"
}

group = "xyz.acrylicstyle"
version = "1.1-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://repo2.acrylicstyle.xyz/") }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("xyz.acrylicstyle", "java-util-all", "0.14.3")
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "utf-8"
    }

    withType<ProcessResources> {
        filteringCharset = "UTF-8"
        from(sourceSets.main.get().resources.srcDirs) {
            include("**")
        }
        from(projectDir) { include("LICENSE") }
    }

    withType<Jar> {
        manifest {
            attributes(
                "Main-Class" to "xyz.acrylicstyle.mappingRemapper.MappingRemapperApp"
            )
        }
        from(configurations.getByName("implementation").apply { isCanBeResolved = true }.map { if (it.isDirectory) it else zipTree(it) })
    }
}
