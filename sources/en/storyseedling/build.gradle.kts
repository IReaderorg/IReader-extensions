listOf("en").map { lang ->
  Extension(
    name = "StorySeedling",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "Novel source based on storyseedling.com",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register) 