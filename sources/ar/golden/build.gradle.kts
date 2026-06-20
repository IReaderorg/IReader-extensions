listOf("ar").map { lang ->
    Extension(
        name = "Golden",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Golden Rest - Arabic Manga/Novel Source",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
