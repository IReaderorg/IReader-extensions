listOf("ar").map { lang ->
    Extension(
        name = "AzoraFly",
        versionCode = 2,
        libVersion = "2",
        lang = lang,
        description = "AzoraFly - روايات عربية مترجمة",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
