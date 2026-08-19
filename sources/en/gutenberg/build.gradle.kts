listOf("en").map { lang ->
    Extension(
        name = "Project Gutenberg",
        versionCode = 4,
        libVersion = "2",
        lang = lang,
        description = "Project Gutenberg — 70,000+ free classic ebooks in the public domain",
        nsfw = false,
        // Use Project Gutenberg's official 144×144 logo served from their own CDN as
        // the extension icon URL. DEFAULT_ICON would point at the 67-byte placeholder
        // bundled with every extension. An http URL here gets written as the
        // `source.icon` meta-data that IReader's CatalogLoader reads at install time.
        icon = "https://www.gutenberg.org/gutenberg/pg-logo-144x144.png",
        assetsDir = "en/gutenberg/main/assets",
        // Pin sourceId to MATCH the runtime `id` overridden in Gutenberg.kt
        // (8923715634821007L). The running Source reports that id, so saved library
        // entries reference it and install metadata must use the same value or the
        // catalog can't bind books to the source. (Previously 1028727279667440734L —
        // the auto-generated MD5 — which never matched the hardcoded class id.)
        sourceId = 8923715634821007L,
        // Pin applicationId so the APK filename and cache dir stay
        // `ireader.gutenberg.en` instead of regenerating to
        // `ireader.project.gutenberg.en` from the new name.
        applicationId = "ireader.gutenberg.en",
        // We explicitly override `id` in Gutenberg.kt and pin sourceId above; disable
        // KSP auto-id so nothing else tries to generate/reference a different constant.
        useAutoSourceId = false,
        // Pure-JVM/Android extension; no JS/iOS target (the app dropped iOS+JS).
        enableJs = false,
    )
}.also(::register)
