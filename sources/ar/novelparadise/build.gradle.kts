listOf("ar").map { lang ->
  Extension(
    name = "NovelParadise",
    versionCode = 6,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false)
}.also(::register)
