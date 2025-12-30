listOf("ru").map { lang ->
    Extension(
        name = "NovelOvh",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "НовелОВХ - Russian novel site with JSON API",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
