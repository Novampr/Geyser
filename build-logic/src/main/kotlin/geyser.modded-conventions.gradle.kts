@file:Suppress("UnstableApiUsage")

import net.fabricmc.loom.task.RemapJarTask
import org.gradle.kotlin.dsl.dependencies

plugins {
    id("geyser.platform-conventions")
    id("architectury-plugin")
    id("dev.architectury.loom")
}

// These are provided by Minecraft/modded platforms already, no need to include them
provided("com.google.code.gson", "gson")
provided("com.google.guava", ".*")
provided("org.slf4j", "slf4j-api")
provided("com.nukkitx.fastutil", ".*")
provided("org.cloudburstmc.fastutil.maps", ".*")
provided("org.cloudburstmc.fastutil.sets", ".*")
provided("org.cloudburstmc.fastutil.commons", ".*")
provided("org.cloudburstmc.fastutil", ".*")
provided("org.checkerframework", "checker-qual")
provided("io.netty", "netty-transport-classes-epoll")
provided("io.netty", "netty-transport-native-epoll")
provided("io.netty", "netty-transport-native-unix-common")
provided("io.netty", "netty-transport-classes-kqueue")
provided("io.netty", "netty-transport-native-kqueue")
provided("io.netty", "netty-transport-native-io_uring")
provided("io.netty", "netty-transport-classes-io_uring")
provided("io.netty", "netty-handler")
provided("io.netty", "netty-common")
provided("io.netty", "netty-buffer")
provided("io.netty", "netty-resolver")
provided("io.netty", "netty-transport")
provided("io.netty", "netty-codec")
provided("io.netty", "netty-codec-base")
provided("org.ow2.asm", "asm")

// cloud-fabric/cloud-neoforge jij's all cloud depends already
provided("org.incendo", ".*")
provided("io.leangen.geantyref", "geantyref")

architectury {
    minecraft = libs.minecraft.get().version as String
}

loom {
    silentMojangMappingsLicense()
}

indra {
    javaVersions {
        target(21)
    }
}

configurations {
    create("includeTransitive").isTransitive = true
    create("shadowBundle") {
        isCanBeResolved = true
        isCanBeConsumed = false
        isTransitive = false
    }
}

tasks {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this task, sources will not be generated.
    sourcesJar {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    shadowJar {
        // Mirrors the example fabric project, otherwise tons of dependencies are shaded that shouldn't be
        configurations = listOf(project.configurations.getByName("shadowBundle"))
        // The remapped shadowJar is the final desired mod jar
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("shaded")
    }

    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    register("remapModrinthJar", RemapJarTask::class) {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
        archiveVersion.set(versionName(project))
        archiveClassifier.set("")
    }
}

afterEvaluate {
    val providedDependencies = providedDependencies[project.name]!!
    val shadedDependencies = configurations.getByName("shadowBundle")
        .dependencies.stream().map { dependency -> "${dependency.group}:${dependency.name}" }.toList()

    // Now: Include all transitive dependencies that aren't excluded
    configurations["includeTransitive"].resolvedConfiguration.resolvedArtifacts.forEach { dep ->
        val name = "${dep.moduleVersion.id.group}:${dep.moduleVersion.id.name}"
        if (!shadedDependencies.contains(name) and !providedDependencies.contains(name)
            and !providedDependencies.contains("${dep.moduleVersion.id.group}:.*")
        ) {
            println("Including dependency via JiJ: ${dep.id}")
            dependencies.add("include", dep.moduleVersion.id.toString())
        } else {
            println("Not including ${dep.id} for ${project.name}!")
        }
    }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
}
