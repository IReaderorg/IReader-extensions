listOf("ar").map { lang ->
  Extension(
    name = "KolNovel",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
