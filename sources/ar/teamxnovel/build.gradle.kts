listOf("ar").map { lang ->
  Extension(
    name = "Teamxnovel",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
