listOf("ar").map { lang ->
    Extension(
        name = "SeaNovel",
        versionCode = 2,
        libVersion = "2",
        lang = lang,
        description = "روايات عربية مترجمة - بحر الروايات",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
