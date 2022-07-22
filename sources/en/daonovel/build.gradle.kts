listOf("en").map { lang ->
  Extension(
    name = "DaoNovel",
    versionCode = 4,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
