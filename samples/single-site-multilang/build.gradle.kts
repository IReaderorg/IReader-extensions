listOf("en", "es").map { lang ->
  Extension(
    name = "SingleSiteMultiLang",
    versionCode = 1,
    libVersion = "1.0",
    lang = lang
  )
}.also(::register)
