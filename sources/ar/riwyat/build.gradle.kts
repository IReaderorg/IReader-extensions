listOf("ar").map { lang ->
  Extension(
    name = "Riwyat",
    versionCode = 12,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
    assetsDir = "ar/riwyat/main/assets",
  )
}.also(::register)
