listOf("ar").map { lang ->
  Extension(
    name = "KolNovel",
    versionCode = 12,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
