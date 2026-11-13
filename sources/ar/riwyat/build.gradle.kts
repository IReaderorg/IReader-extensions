listOf("ar").map { lang ->
  Extension(
    name = "Riwyat",
    versionCode = 13,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
