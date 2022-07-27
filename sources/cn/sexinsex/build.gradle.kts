listOf("cn").map { lang ->
  Extension(
    name = "sexinsex",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = true,
  )
}.also(::register)
