listOf("en").map { lang ->
  Extension(
    name = "MyLoveNovel",
    versionCode = 9,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
