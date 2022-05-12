listOf("en").map { lang ->
  Extension(
    name = "FreeWebNovel",
    versionCode = 5,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
