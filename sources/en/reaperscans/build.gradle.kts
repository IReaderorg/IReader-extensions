listOf("en").map { lang ->
  Extension(
    name = "ReaperScans",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
