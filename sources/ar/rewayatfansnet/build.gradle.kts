listOf("ar").map { lang ->
  Extension(
    name = "RewayatfansNet",
    versionCode = 5,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
