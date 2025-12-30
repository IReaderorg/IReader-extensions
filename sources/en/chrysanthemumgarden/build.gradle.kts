listOf("en").map { lang ->
    Extension(
        name = "ChrysanthemumGarden",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
