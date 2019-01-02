listOf("en", "es").map { lang ->
  Extension(
    name = "MangaDex",
    versionCode = 44,
    libVersion = "1.0",
    lang = lang,
    description = "Highest quality and scanlator-approved source",
    nsfw = false,
    deepLinks = listOf(
      DeepLink(
        host = "mangadex.org",
        scheme = "https",
        pathPattern = "/title/.*"
      ),
      DeepLink(
        host = "mangadex.org",
        scheme = "https",
        pathPattern = "/chapter/.*"
      )
    )
  )
}.also(::register)
