listOf("en").map { lang ->
  Extension(
    name = "LightNovelsMe",
    versionCode = 5,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
