listOf("en").map { lang ->
  Extension(
    name = "NovelOwlCom",
    versionCode = 3,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
