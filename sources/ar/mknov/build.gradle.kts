listOf("ar").map { lang ->
  Extension(
    name = "MKNOV",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "قراءة الروايات المترجمة من مملكة الروايات",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)