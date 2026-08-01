listOf("ar").map { lang ->
    Extension(
        name = "HizoManga",
        versionCode = 4, // Bumped from 3 as per commit message
        libVersion = "2",
        lang = lang,
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON // This will be resolved by ManifestProcessor.kt
    )
}.also(::register)
