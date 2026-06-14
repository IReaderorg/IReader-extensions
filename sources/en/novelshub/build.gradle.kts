listOf("en").map { lang ->
    Extension(
        name = "Novelshub",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Read novels from Novelshub (NovelDex)",
        nsfw = true,
        icon = DEFAULT_ICON,
        assetsDir = "en/novelshub/main/assets",
    )
}.also(::register)
