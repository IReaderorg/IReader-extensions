listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite",
    versionCode = 5,
    libVersion = "1.4",
    lang = lang,
    description = "Highest quality and scanlator-approved source",
    nsfw = false,
  )
}.also(::register)
