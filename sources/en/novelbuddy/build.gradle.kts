listOf("en").map { lang ->
    Extension(
        name = "NovelBuddy",
        versionCode = 1,
        libVersion = "1",
        lang = lang,
        description = "Novel source based on novelbuddy.com",
        nsfw = false,
        icon = DEFAULT_ICON
    )
}.also(::register)
