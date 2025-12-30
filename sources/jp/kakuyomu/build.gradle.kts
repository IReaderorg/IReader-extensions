listOf("jp").map { lang ->
    Extension(
        name = "Kakuyomu",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Kakuyomu - Japanese novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
