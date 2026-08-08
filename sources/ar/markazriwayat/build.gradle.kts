listOf("ar").map { lang ->
  Extension(
    name = "MarkazRiwayat",
    versionCode = 5,
    libVersion = "3",
    lang = lang,
    description = "مركز الروايات - روايات عربية مترجمة",
    nsfw = false)
}.also(::register)
