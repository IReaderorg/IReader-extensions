listOf("en").map { lang ->
  Extension(
    name = "MtlNovelCom",
    versionCode = 4,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
