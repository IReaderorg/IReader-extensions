listOf("vi").map { lang ->
    Extension(
        name = "TruyenFull",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Truyện Full - Vietnamese novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
