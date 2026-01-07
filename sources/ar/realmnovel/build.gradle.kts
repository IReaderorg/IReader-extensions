listOf("ar").map { lang ->
  Extension(
    name = "Realmnovel",
    versionCode = 4,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
