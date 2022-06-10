listOf("en").map { lang ->
  Extension(
    name = "LightNovelPub",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
