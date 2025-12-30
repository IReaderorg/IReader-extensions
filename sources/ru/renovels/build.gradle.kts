listOf("ru").map { lang ->
    Extension(
        name = "Renovels",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Renovels - Russian novel site with JSON API",
        nsfw = true,
        icon = DEFAULT_ICON,
    )
}.also(::register)
