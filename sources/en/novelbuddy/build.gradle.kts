listOf("en").map { lang ->
    Extension(
        name = "Novelbuddy",
        versionCode = 11,
        libVersion = "2",
        lang = lang,
        description = "Read novels from NovelBuddy.io",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "en/novelbuddy/main/assets",
    )
}.also(::register)
