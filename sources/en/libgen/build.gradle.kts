listOf("en").map { lang ->
    Extension(
        name = "Library Genesis",
        versionCode = 6,
        libVersion = "2",
        lang = lang,
        description = "Library Genesis — in-app EPUB reading (chapters parsed from the downloaded EPUB); PDF/MOBI items fall back to an external download link",
        // Content primarily public-domain and academic, but LibGen hosts copyrighted
        // material too. Flagging so the user sees the NSFW/legal-gray warning in
        // IReader's source browser if they have that filter on.
        nsfw = true,
        icon = "https://libgen.li/img/favicon.ico",
        assetsDir = "en/libgen/main/assets",
        sourceId = 8146321774592308271L,
        applicationId = "ireader.libgen.en",
        useAutoSourceId = false,
        // Pure-JVM extension (java.util.zip / java.io for in-extension EPUB parsing);
        // those APIs don't exist in Kotlin/JS, so the JS/iOS build path must be off.
        enableJs = false,
    )
}.also(::register)
