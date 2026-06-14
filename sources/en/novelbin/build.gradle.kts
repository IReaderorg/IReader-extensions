listOf("en").map { lang ->
    Extension(
        name = "NovelBin",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Read novels from NovelBin",
        nsfw = true,
        icon = DEFAULT_ICON,
        assetsDir = "en/novelbin/main/assets",
    )
}.also(::register)
