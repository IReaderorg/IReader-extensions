listOf("en").map { lang ->
  Extension(
    name = "MtlNation",
    versionCode = 4,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
