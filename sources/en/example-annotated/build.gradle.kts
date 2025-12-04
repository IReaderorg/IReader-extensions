listOf("en").map { lang ->
    Extension(
        name = "ExampleSource",
        versionCode = 1,
        libVersion = "1",
        lang = lang,
        description = "Example source demonstrating KSP annotations",
        nsfw = false,
        icon = DEFAULT_ICON
    )
}.also(::register)
