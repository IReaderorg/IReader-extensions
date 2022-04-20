listOf("en").map { lang ->
  Extension(
    name = "webnovel",
    versionCode = 4,
    libVersion = "1.3",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
