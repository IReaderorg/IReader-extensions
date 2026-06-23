listOf("ar").map { lang ->
    Extension(
        name = "KolNovel",
        versionCode = 2,
        libVersion = "2",
        lang = lang,
        description = "ملوك الروايات - روايات عربية مترجمة",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
