listOf("en").map { lang ->
  Extension(
    name = "DaoNovel",
    versionCode = 7,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
