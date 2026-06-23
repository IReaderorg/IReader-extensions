listOf("ar").map { lang ->
    Extension(
        name = "Golden",
        versionCode = 2,
        libVersion = "2",
        lang = lang,
        description = "Golden Rest - Arabic Novel Source",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
