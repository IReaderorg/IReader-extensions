listOf("en").map { lang ->
  Extension(
    name = "Ranobes",
    versionCode = 7,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
