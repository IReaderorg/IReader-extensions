listOf("en").map { lang ->
  Extension(
    name = "FastNovel",
    versionCode = 2,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
    dependencies = {

    }
  )
}.also(::register)
