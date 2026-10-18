@file:Suppress("UnstableApiUsage")

plugins {
	id("net.fabricmc.fabric-loom")
}

val minecraftVersion = "26.2"
val minecraftFriendly = "26.2"
val modmenu = "18.0.0-alpha.8"
val fapi = "0.154.1+26.2"
group = project.property("maven_group") as String
version = "${project.property("version")}+$minecraftFriendly"
base.archivesName = "AxolotlClient"

loom {
	accessWidenerPath.set(file("src/main/resources/axolotlclient.classtweaker"))
	mods {
		create("axolotlclient") {
			sourceSet("main")
		}
		create("axolotlclient-test") {
			sourceSet("test")
		}
	}
}

repositories {
	maven("https://maven.noxcrew.com/public")
	maven("https://maven.enginehub.org/repo/")
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	implementation("net.fabricmc:fabric-loader:${project.property("fabric_loader")}")

	implementation("net.fabricmc.fabric-api:fabric-api:$fapi")

	implementation("io.github.axolotlclient:AxolotlClient-config:${project.property("config")}+$minecraftFriendly")
	include("io.github.axolotlclient:AxolotlClient-config:${project.property("config")}+$minecraftFriendly")
	implementation(include("io.github.axolotlclient:AxolotlClient-config-rounded:${project.property("config")}+$minecraftFriendly")!!)

	compileOnly("com.terraformersmc:modmenu:$modmenu")

	api(include(project(path = ":common", configuration = "shadow"))!!)

	val noxesiumVersion = "3.1.0"
	compileOnly("maven.modrinth:noxesium:$noxesiumVersion")
	implementation("com.noxcrew.noxesium:api:$noxesiumVersion")
	//localRuntime("org.khelekore:prtree:1.5")

	compileOnly("maven.modrinth:e4mc:6.1.2-fabric")

	implementation("net.hypixel:mod-api:1.0.2")
	include(implementation("maven.modrinth:hypixel-mod-api:1.0.2+build.1+mc26.1")!!)
}

tasks.processResources {
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType(JavaCompile::class).configureEach {
	options.encoding = "UTF-8"

	if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_26)) {
		options.release = 25
	}
}

java {
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.runClient {
	classpath(sourceSets.getByName("test").runtimeClasspath)
	jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-XX:+IgnoreUnrecognizedVMOptions")
}

// Configure the maven publication
publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			artifactId = base.archivesName.get()
			from(components["java"])
		}
	}

	repositories {
		maven {
			name = "owlMaven"
			val repository = if (project.version.toString().contains("beta") || project.version.toString().contains("alpha")) "snapshots" else "releases"
			url = uri("https://maven.axolotlclient.com/$repository")
			credentials(PasswordCredentials::class)
			authentication {
				create<BasicAuthentication>("basic")
			}
		}
	}
}

afterEvaluate {
	tasks.getByName("publishModrinth") {
		dependsOn(rootProject.tasks.getByName("generateVersionChangelog"))
	}
	tasks.getByName("publishCurseforge") {
		dependsOn(rootProject.tasks.getByName("generateVersionChangelog"))
	}
}

publishMods {
	file.set(tasks.jar.flatMap { it.archiveFile })
	additionalFiles.from(tasks.sourcesJar.flatMap { it.archiveFile })
	changelog.set(rootProject.layout.buildDirectory.file("changelog").map { it.asFile.readText() })
	type.set(STABLE)
	modLoaders.add("fabric")


	modrinth {
		accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
		projectId.set("p2rxzX0q")
		minecraftVersions.set(listOf(minecraftVersion))
		requires { slug = "fabric-api" }
		embeds { slug = "axolotlclient-rendering" }
	}

	curseforge {
		accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
		minecraftVersions.set(listOf(minecraftVersion))
		projectId.set("809392")
		requires { slug = "fabric-api" }
		client = true
	}
}
