listOf("jp").map { lang ->
    Extension(
        name = "Syosetu",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Syosetu - Japanese novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
