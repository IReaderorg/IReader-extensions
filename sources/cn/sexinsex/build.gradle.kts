listOf("cn").map { lang ->
  Extension(
    name = "sexinsex",
    versionCode = 2,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = true,
    icon = DEFAULT_ICON,
    type = ExtensionType.MultiSrc
  )
}.also(::register)
