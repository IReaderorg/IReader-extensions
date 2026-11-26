listOf("en").map { lang ->
    Extension(
        name = "NovelDex",
        versionCode = 12,
        libVersion = "2",
        lang = lang,
        description = "Read novels and comics from NovelDex",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
