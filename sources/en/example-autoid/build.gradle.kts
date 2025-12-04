listOf("en").map { lang ->
    Extension(
        name = "ExampleAutoId",
        versionCode = 1,
        libVersion = "1",
        lang = lang,
        description = "Example source demonstrating @AutoSourceId usage",
        nsfw = false,
        icon = DEFAULT_ICON,
        // Note: sourceId is auto-generated! No need to specify manually.
        // The ID comes from: hash("exampleautoid/en/1")
    )
}.also(::register)
