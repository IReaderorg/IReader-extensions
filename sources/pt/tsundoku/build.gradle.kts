listOf("pt").map { lang ->
    Extension(
        name = "Tsundoku",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Tsundoku Traduções - Portuguese novel translations",
        nsfw = true,
        icon = DEFAULT_ICON,
    )
}.also(::register)
