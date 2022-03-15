listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite.co",
    versionCode = 4,
    libVersion = "1.0",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
