listOf("ar").map { lang ->
  Extension(
    name = "ProComicPro",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "برو كوالك (procomic.pro) - مانجا ومانهوا ومانها وروايات مترجمة",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
