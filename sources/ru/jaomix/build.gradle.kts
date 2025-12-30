listOf("ru").map { lang ->
    Extension(
        name = "Jaomix",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
