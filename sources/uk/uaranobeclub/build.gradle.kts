listOf("uk").map { lang ->
    Extension(
        name = "UaRanobeClub",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "UA Ranobe Club - Ukrainian novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
