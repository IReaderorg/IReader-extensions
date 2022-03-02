listOf("en").map { lang ->
  Extension(
    name = "MyLoveNovel",
    versionCode = 1,
    libVersion = "1.0",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
