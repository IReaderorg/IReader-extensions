listOf("ar").map { lang ->
    Extension(
        name = "SeaNovel",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "روايات عربية مترجمة - بحر الروايات",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
