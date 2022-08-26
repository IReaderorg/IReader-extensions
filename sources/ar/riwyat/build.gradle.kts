listOf("ar").map { lang ->
  Extension(
    name = "Riwyat",
    versionCode = 5,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
