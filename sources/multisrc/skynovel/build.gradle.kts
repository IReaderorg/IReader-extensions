listOf(
    Extension(
        name = "SkyNovel",
        versionCode = 6,
        libVersion = "2",
        lang = "en",
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "multisrc/skynovel/skynovel/assets",
        sourceDir = "skynovel",
    ),
    Extension(
        name = "WbNovel",
        versionCode = 2,
        libVersion = "2",
        lang = "in",
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "multisrc/skynovel/wbnovel/assets",
        sourceDir = "wbnovel",
    ),
).also(::register)
