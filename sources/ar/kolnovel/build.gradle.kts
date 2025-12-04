listOf("ar").map { lang ->
  Extension(
    name = "KolNovel",
    versionCode = 14,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    enableJs = true)
}.also(::register)
