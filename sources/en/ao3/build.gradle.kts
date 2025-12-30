listOf("en").map { lang ->
    Extension(
        name = "ArchiveOfOurOwn",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Archive Of Our Own (AO3) - Fanfiction archive",
        nsfw = true,
        icon = DEFAULT_ICON,
    )
}.also(::register)
