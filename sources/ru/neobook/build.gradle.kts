listOf("ru").map { lang ->
    Extension(
        name = "Neobook",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Neobook - Russian novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
