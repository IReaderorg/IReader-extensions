listOf("en").map { lang ->
  Extension(
    name = "Wnmtl",
    versionCode = 6,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
