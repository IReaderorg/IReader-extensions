listOf("ar").map { lang ->
    Extension(
        name = "GalaxyNovels",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "مجرة الروايات - قراءة الروايات المترجمة بجودة عالية",
        nsfw = false,
        icon = DEFAULT_ICON,
        sourceId = 5839019927924950627L,
    )
}.also(::register)
