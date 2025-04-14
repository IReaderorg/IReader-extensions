listOf("en").map { lang ->
  Extension(
    name = "LibRead",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "Novel source based on libread.com",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register) 