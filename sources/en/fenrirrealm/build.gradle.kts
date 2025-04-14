listOf("en").map { lang ->
  Extension(
    name = "FenrirRealm",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "Novel source based on fenrirealm.com",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register) 