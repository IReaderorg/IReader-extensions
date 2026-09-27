listOf("ar").map { lang ->
    Extension(
        name = "NovelParadise",
        versionCode = 7,
        libVersion = "2",
        lang = lang,
        description = "NovelParadise - جنة الروايات",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)