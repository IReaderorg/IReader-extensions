listOf("en").map { lang ->
    Extension(
        name = "KDTNovels",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "KDT Novels - Madara-based novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
