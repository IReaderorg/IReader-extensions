listOf("tu").map { lang ->
    Extension(
        name = "Novebo",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Novels from Novebo",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "tu/novebo/main/assets",
    )
}.also(::register)
