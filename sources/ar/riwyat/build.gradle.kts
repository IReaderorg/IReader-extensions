listOf("ar").map { lang ->
  Extension(
    name = "Riwyat",
    versionCode = 10,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
