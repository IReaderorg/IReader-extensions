listOf("en").map { lang ->
  Extension(
    name = "FastNovel",
    versionCode = 3,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
