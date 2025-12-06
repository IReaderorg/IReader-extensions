listOf("en").map { lang ->
    Extension(
        name = "MyDramaNovel",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Translating Tomorrow's TV Drama Hits, Straight from the Pages of Chinese Novels",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
