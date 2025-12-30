listOf("en").map { lang ->
    Extension(
        name = "Webnovel",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Webnovel - Popular web novel platform",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
