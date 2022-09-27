listOf("en").map { lang ->
  Extension(
    name = "ReadWnCom",
    versionCode = 2,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
