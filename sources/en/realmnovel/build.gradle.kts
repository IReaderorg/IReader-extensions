listOf("en").map { lang ->
    Extension(
        name = "RealmNovel",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Read novels from RealmNovel",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "en/realmnovel/main/assets",
    )
}.also(::register)
