listOf("en").map { lang ->
  Extension(
    name = "KoreanOnline",
    versionCode = 7,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
