listOf("en").map { lang ->
    Extension(
        name = "ReadNovelFullPlugin",
        versionCode = 10,
        libVersion = "1",
        lang = lang,
        description = "Read novels from ",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "en//main/assets",
    )
}.also(::register)
