listOf("en").map { lang ->
    Extension(
        name = "Rainofsnow",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Rainofsnow - English novel translations",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
