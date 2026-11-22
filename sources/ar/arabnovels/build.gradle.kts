listOf("ar").map { lang ->
  Extension(
    name = "ArabNovels",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "منصة الروايات العربية - روايات متروجة ومؤلفة بالعربية (arabnovels.com)",
    nsfw = false,
  )
}.also(::register)
