listOf("ar").map { lang ->
  Extension(
    name = "NovelParadise",
    versionCode = 5,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false)
}.also(::register)
