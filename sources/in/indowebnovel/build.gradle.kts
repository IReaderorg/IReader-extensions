listOf("in").map { lang ->
  Extension(
    name = "IndoWebNovel",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
