listOf("ar").map { lang ->
  Extension(
    name = "RiwyatArab",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "روايات عرب - قراءة الروايات العربية مجاناً",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
