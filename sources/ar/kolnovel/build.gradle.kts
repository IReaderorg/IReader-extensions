listOf("ar").map { lang ->
  Extension(
    name = "KolNovel",
    versionCode = 16,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false)
}.also(::register)
