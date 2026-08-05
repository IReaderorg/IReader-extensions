listOf("en").map { lang ->
    Extension(
        name = "Standard Ebooks",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Standard Ebooks — beautifully typeset public-domain classics",
        nsfw = false,
        // Official Standard Ebooks touch icon.
        icon = "https://standardebooks.org/apple-touch-icon.png",
        assetsDir = "en/standardebooks/main/assets",
        // Pinned so future renames don't orphan library entries.
        sourceId = 3721845692038715349L,
        applicationId = "ireader.standardebooks.en",
        useAutoSourceId = false,
        // Pure-JVM/Android extension; no JS/iOS target (the app dropped iOS+JS).
        enableJs = false,
    )
}.also(::register)
