listOf("ar").map { lang ->
  Extension(
    name = "Rewayatfans",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
