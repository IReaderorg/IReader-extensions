listOf("ar").map { lang ->
  Extension(
    name = "MarkazRiwayat",
    versionCode = 4,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false)
}.also(::register)
