listOf("kr").map { lang ->
    Extension(
        name = "Agitoon",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Agitoon - Korean novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
