listOf("cn").map { lang ->
  Extension(
    name = "sexinsex",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = true,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
