import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("multiplatform") version "2.2.21"
}

kotlin {
    js {
        browser()
        useEsModules()
        binaries.executable()
    }
    sourceSets {
        jsMain {
            // The exact same simulation and rasteriser the app uses.
            kotlin.srcDir("../core/src/main/kotlin")
        }
    }
}

/** One self-contained page: src/preview.html with the compiled script inlined. */
val preview by tasks.registering {
    val compiled = tasks.named("compileProductionExecutableKotlinJs")
    dependsOn(compiled)
    val template = layout.projectDirectory.file("src/preview.html")
    val output = layout.buildDirectory.file("preview/bear-preview.html")
    inputs.file(template)
    inputs.files(compiled)
    outputs.file(output)
    doLast {
        val dir = compiled.get().outputs.files.files.first { it.isDirectory }
        val script = dir.walkTopDown().single { it.extension == "mjs" && !it.name.endsWith(".map") }.readText()
            .replace(Regex("//# sourceMappingURL=.*"), "")
        val commit = runCatching {
            ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(projectDir).start().inputStream.bufferedReader().readText().trim()
        }.getOrDefault("local")
        val stamp = "Build $commit · " + ZonedDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'"))
        val page = template.asFile.readText()
            .replace("/*BEAR_VERSION*/", stamp)
            .replace("/*BEAR_SCRIPT*/", script.replace("</script", "<\\/script"))
        output.get().asFile.apply { parentFile.mkdirs(); writeText(page) }
        println("Wrote ${output.get().asFile}")
    }
}
