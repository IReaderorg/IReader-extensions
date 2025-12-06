listOf("en").map { lang ->
    Extension(
        name = "WuxiaClick",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Read Wuxia, Light and Korean Novels at WuxiaClick",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
