listOf("en").map { lang ->
  Extension(
    name = "FreeWebNovel",
    versionCode = 19,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
    // Opt this source into the JS bundle used by the iOS JavaScriptCore runtime.
    // js-sources discovery and build_js.yml both scan for this literal.
    enableJs = true
  )
}.also(::register)
