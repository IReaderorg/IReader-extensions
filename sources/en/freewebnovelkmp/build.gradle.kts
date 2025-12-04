listOf("en").map { lang ->
    Extension(
        name = "FreeWebNovelKmp",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "FreeWebNovel - KMP compatible source for all platforms",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "en/freewebnovelkmp/main/assets",
        // Enable JS build for iOS support
        enableJs = true,
    )
}.also(::register)
