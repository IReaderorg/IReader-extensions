listOf("ar").map { lang ->
  Extension(
    name = "Teamxnovel",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
