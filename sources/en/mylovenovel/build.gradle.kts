listOf("en").map { lang ->
  Extension(
    name = "MyLoveNovel",
    versionCode = 6,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
