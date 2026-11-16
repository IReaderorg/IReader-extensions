listOf("ar").map { lang ->
  Extension(
    name = "ProComicPro",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "برو كوالك (procomic.pro) - مانجا ومانهوا ومانها وروايات مترجمة",
    nsfw = false,
    icon = DEFAULT_ICON,
    remoteDependencies = setOf("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0"),
  )
}.also(::register)
