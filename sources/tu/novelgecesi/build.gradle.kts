listOf("tu").map { lang ->
    Extension(
        name = "NovelGecesi",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Türkçe novel okuma sitesi",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
