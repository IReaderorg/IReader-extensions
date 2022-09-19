listOf("en").map { lang ->
  Extension(
    name = "NovelTop1Net",
    versionCode = 2,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
    type = ExtensionType.MultiSrc
  )
}.also(::register)
