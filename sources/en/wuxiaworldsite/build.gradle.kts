listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite",
    versionCode = 2,
    libVersion = "1.1",
    lang = lang,
    description = "Highest quality and scanlator-approved source",
    nsfw = false,
  )
}.also(::register)
