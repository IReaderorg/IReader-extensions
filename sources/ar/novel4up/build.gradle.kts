listOf("ar").map { lang ->
  Extension(
    name = "Novel4Up",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    enableJs = true)
}.also(::register)
