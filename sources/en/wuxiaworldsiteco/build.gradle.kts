listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSiteco",
    versionCode = 6,
    libVersion = "1.0",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
