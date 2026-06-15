listOf("ru").map { lang ->
    Extension(
        name = "Ranobes",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Ranobes - Russian ranobe/novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "ru/ranobes/main/assets",
    )
}.also(::register)
