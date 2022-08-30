listOf("en").map { lang ->
  Extension(
    name = "WebNovelSite",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
