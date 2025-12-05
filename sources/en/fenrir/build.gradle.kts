listOf("en").map { lang ->
    Extension(
        name = "Fenrir",
        versionCode = 11,
        libVersion = "2",
        lang = lang,
        description = "Read novels from Fenrir Realm",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "en/fenrir/main/assets",
    )
}.also(::register)
