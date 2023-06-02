listOf(
    Extension(
        name = "SkyNovel",
        versionCode = 5,
        libVersion = "1",
        lang = "en",
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "multisrc/skynovel/skynovel/assets",
        sourceDir = "skynovel",
    ),
    Extension(
        name = "WbNovel",
        versionCode = 1,
        libVersion = "1",
        lang = "in",
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "multisrc/skynovel/wbnovel/assets",
        sourceDir = "wbnovel",
    ),
).also(::register)