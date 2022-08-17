listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite",
    versionCode = 9,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
