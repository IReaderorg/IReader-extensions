listOf("en").map { lang ->
    Extension(
        name = "DivineDaoLibrary",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Divine Dao Library - English novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
