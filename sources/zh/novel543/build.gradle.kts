listOf("zh").map { lang ->
    Extension(
        name = "Novel543",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Novel543 - Chinese novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
