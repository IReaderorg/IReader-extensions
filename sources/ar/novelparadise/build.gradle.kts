listOf("ar").map { lang ->
  Extension(
    name = "NovelParadise",
    versionCode = 4,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    enableJs = true)
}.also(::register)
