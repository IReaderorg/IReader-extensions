listOf("cn").map { lang ->
  Extension(
    name = "Aixdzs",
    versionCode = 4,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
