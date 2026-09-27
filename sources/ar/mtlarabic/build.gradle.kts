listOf("ar").map { lang ->
    Extension(
        name = "MtlArabic",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "مكتبة الخيال - روايات عربية مترجمة",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
