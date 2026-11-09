listOf("ar").map { lang ->
    Extension(
        name = "NovelTime",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "نوفل تايم - روايات ويب مترجمة للعربية",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)