listOf("ar").map { lang ->
    Extension(
        name = "GalaxyNovels",
        versionCode = 6,
        libVersion = "2",
        lang = lang,
        description = "مجرة الروايات - قراءة الروايات المترجمة بجودة عالية",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
