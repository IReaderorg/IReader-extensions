listOf("en").map { lang ->
  Extension(
    name = "LibRead",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "Novel source based on libread.com",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
