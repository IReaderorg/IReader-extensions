listOf("ar").map { lang ->
  Extension(
    name = "NovelParadise",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
