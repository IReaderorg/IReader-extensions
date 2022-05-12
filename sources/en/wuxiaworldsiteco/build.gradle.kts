listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite.co",
    versionCode = 6,
    libVersion = "1.0",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
