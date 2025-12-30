listOf("ar").map { lang ->
  Extension(
    name = "RewayatClub",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "Arabic novels from Rewayat Club",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
