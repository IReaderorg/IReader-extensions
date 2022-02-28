listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite",
    versionCode = 1,
    libVersion = "1.0",
    lang = lang,
    description = "Highest quality and scanlator-approved source",
    nsfw = false,
  )
}.also(::register)
