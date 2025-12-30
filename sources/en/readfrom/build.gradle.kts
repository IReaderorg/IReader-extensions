listOf("en").map { lang ->
    Extension(
        name = "ReadFrom",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Read From Net - English novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
