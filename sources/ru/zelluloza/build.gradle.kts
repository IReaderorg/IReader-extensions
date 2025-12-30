listOf("ru").map { lang ->
    Extension(
        name = "Zelluloza",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Целлюлоза - Russian novel site with encrypted content",
        nsfw = true,
        icon = DEFAULT_ICON,
    )
}.also(::register)
