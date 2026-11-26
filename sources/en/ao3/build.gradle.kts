listOf("en").map { lang ->
    Extension(
        name = "ArchiveOfOurOwn",
        versionCode = 8,
        libVersion = "2",
        lang = lang,
        description = "Archive Of Our Own (AO3) - fanfiction archive with search filters",
        nsfw = true,
        icon = DEFAULT_ICON,
    )
}.also(::register)
