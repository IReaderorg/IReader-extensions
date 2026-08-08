listOf("ar").map { lang ->
  Extension(
    name = "MarkazRiwayat",
    versionCode = 6,
    libVersion = "2",
    lang = lang,
    description = "مركز الروايات - روايات عربية مترجمة",
    nsfw = false)
}.also(::register)
