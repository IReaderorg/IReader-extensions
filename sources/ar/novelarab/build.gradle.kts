listOf("ar").map { lang ->
    Extension(
        name = "NovelArab",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Novels from NovelArab",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "ar/novelarab/main/assets",
    )
}.also(::register)
