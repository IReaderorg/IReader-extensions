listOf("en").map { lang ->
  Extension(
    name = "DaoNovel",
    versionCode = 5,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
    type = ExtensionType.MultiSrc,
  )
}.also(::register)
