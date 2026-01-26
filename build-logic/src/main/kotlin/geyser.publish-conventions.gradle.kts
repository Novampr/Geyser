plugins {
    id("geyser.shadow-conventions")
    id("net.kyori.indra.publishing")
}

indra {
    publishSnapshotsTo("geysermc", "https://repo.opencollab.dev/maven-snapshots")
    publishReleasesTo("geysermc", "https://repo.opencollab.dev/maven-releases")
}

val signMavenPublication by tasks
signMavenPublication.enabled = false
