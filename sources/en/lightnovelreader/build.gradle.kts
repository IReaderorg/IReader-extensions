listOf("en").map { lang ->
  Extension(
    name = "LightNovelReader",
    versionCode = 4,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
