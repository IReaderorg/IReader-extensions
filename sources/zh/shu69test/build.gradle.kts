listOf("zh").map { lang ->
    Extension(
        name = "Shu69",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "69书吧 - Chinese novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
