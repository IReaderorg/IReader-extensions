listOf("ar").map { lang ->
  Extension(
    name = "Rewayatfans",
    versionCode = 2,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = true,
    icon = DEFAULT_ICON
  )
}.also(::register)
