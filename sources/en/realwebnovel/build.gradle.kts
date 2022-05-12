listOf("en").map { lang ->
  Extension(
    name = "RealWebNovel",
    versionCode = 5,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
