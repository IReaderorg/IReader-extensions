listOf("ar").map { lang ->
    Extension(
        name = "KolNovel",
        versionCode = 2,
        libVersion = "2",
        lang = lang,
        description = "Novels from KolNovel",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "ar/kolnovel/main/assets",
    )
}.also(::register)
