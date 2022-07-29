listOf("en").map { lang ->
  Extension(
    name = "FastNovel",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
