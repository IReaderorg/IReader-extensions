listOf("en").map { lang ->
  Extension(
    name = "RealWebNovel",
    versionCode = 1,
    libVersion = "1.0",
    lang = lang,
    description = "Highest quality and scanlator-approved source",
    nsfw = false,
  )
}.also(::register)
