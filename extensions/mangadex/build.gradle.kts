listOf("en", "es").map { lang ->
  Extension(
    name = "MangaDex",
    versionCode = 44,
    libVersion = "1.0",
    lang = lang,
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
