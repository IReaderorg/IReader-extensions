listOf("ar").map { lang ->
    Extension(
        name = "KolNovel",
        versionCode = 3,
        libVersion = "3",
        lang = lang,
        description = "ملوك الروايات - روايات عربية مترجمة",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
