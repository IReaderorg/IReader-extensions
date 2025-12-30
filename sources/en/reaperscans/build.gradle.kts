listOf("en").map { lang ->
    Extension(
        name = "ReaperScans",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Reaper Scans - English novel site",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
