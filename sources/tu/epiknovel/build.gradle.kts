listOf("tu").map { lang ->
  Extension(
    name = "EpikNovel",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
