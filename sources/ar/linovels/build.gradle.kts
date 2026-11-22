listOf("ar").map { lang ->
  Extension(
    name = "Linovels",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "مملكة الروايات - روايات عربية مترجمة (linovels.com)",
    nsfw = false,
    // Point straight at the site logo so the icon shows up immediately, before
    // the repo icon is published to the repov2 branch by CI.
    icon = "https://linovels.com/wp-content/themes/novels-kingdom/assets/img/logo.png",
  )
}.also(::register)
