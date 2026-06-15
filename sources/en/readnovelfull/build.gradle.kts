listOf("en").map { lang ->
    Extension(
        name = "ReadNovelFull",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Read novels from ReadNovelFull",
        nsfw = true,
        icon = DEFAULT_ICON,
        assetsDir = "en/readnovelfull/main/assets",
    )
}.also(::register)
