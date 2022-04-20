listOf("en").map { lang ->
  Extension(
    name = "RealWebNovel",
    versionCode = 4,
    libVersion = "1.3",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
