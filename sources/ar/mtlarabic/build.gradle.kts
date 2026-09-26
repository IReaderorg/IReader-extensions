listOf("ar").map { lang ->
    Extension(
        name = "MtlArabic",
        versionCode = 2,
        libVersion = "2",
        lang = lang,
        description = "مكتبة الخيال - روايات عربية مترجمة",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
