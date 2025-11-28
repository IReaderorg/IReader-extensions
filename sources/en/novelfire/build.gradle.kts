listOf("en").map { lang ->
    Extension(
        name = "Novelfire",
        versionCode = 10,
        libVersion = "1",
        lang = lang,
        description = "Read novels from Novel Fire",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "en/novelfire/main/assets",
    )
}.also(::register)
