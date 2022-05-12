listOf("en").map { lang ->
  Extension(
    name = "NovelFull",
    versionCode = 5,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
