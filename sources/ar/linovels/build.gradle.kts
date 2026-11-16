listOf("ar").map { lang ->
  Extension(
    name = "Linovels",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "مملكة الروايات - روايات عربية مترجمة (linovels.com)",
    nsfw = false,
    icon = DEFAULT_ICON,
    remoteDependencies = setOf("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0"),
  )
}.also(::register)
