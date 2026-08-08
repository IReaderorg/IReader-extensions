listOf("ar").map { lang ->
  Extension(
    name = "Realmnovel",
    versionCode = 6,
    libVersion = "2",
    lang = lang,
    description = "RealmNovel: روايات عربية مترجمة ومؤلفة",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
