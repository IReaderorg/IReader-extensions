listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite.co",
    versionCode = 5,
    libVersion = "1.0",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
