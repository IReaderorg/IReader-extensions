listOf("en").map { lang ->
  Extension(
    name = "MyLoveNovel",
    versionCode = 8,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
