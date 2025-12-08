listOf("en").map { lang ->
  Extension(
    name = "StorySeedling",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "Novel source based on storyseedling.com",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
