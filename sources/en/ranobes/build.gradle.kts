listOf("en").map { lang ->
  Extension(
    name = "Ranobes",
    versionCode = 9,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
