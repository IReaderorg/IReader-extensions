listOf("en").map { lang ->
  Extension(
    name = "Comrademao",
    versionCode = 1,
    libVersion = "1.0",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
