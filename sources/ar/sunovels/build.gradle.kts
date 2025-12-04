listOf("ar").map { lang ->
  Extension(
    name = "Sunovels",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
