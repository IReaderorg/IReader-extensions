listOf("ar").map { lang ->
  Extension(
    name = "Novel4Up",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
