listOf("zh").map { lang ->
    Extension(
        name = "Ixdzs8",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "爱下电子书 - Chinese novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
