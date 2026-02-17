listOf("ar").map { lang ->
  Extension(
    name = "MarkazRiwayat",
    versionCode = 3,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false)
}.also(::register)
