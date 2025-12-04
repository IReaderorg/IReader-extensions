# Contributing

This guide have some instructions and tips on how to create a new IReader extension. Please **read it carefully** if you're a new contributor or don't have any experience on the required languages and knowledges.

This guide is not definitive and it's being updated over time. If you find any issue on it, feel free to report it through a [Meta Issue](https://github.com/IReaderorg/IReader-extensions/issues/new?assignees=&labels=Meta+request&template=request_meta.yml) or fixing it directly by submitting a Pull Request.

## Table of Contents

1. [Prerequisites](#prerequisites)
    1. [Tools](#tools)
    2. [Cloning the repository](#cloning-the-repository)
2. [Getting help](#getting-help)
3. [Writing an extension](#writing-an-extension)
    1. [Setting up a new Gradle module](#setting-up-a-new-gradle-module)
    2. [Core dependencies](#core-dependencies)
    3. [Extension main class](#extension-main-class)
    4. [Extension call flow](#extension-call-flow)
    5. [Misc notes](#misc-notes)
    6. [Advanced extension features](#advanced-extension-features)
4. [Writing an Multisrc Extension](#writing-an-multisrc-extension)
    1. [Multisrc  Extension file structure](#multisrc-extension-file-structure)
5. [Running](#running)
6. [Building](#building)
6. [Submitting the changes](#submitting-the-changes)
    1. [Pull Request checklist](#pull-request-checklist)

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)
- Web scraping
    - [HTML](https://developer.mozilla.org/en-US/docs/Web/HTML)
    - [CSS selectors](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Selectors)
    - [OkHttp](https://square.github.io/okhttp/)
    - [JSoup](https://jsoup.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled and a recent version of IReader installed
- [Icon Generator](https://icon.kitchen/)
### Cloning the repository

Some alternative steps can be followed to ignore "repo" branch and skip unrelated sources, which will make it faster to pull, navigate and build. This will also reduce disk usage and network traffic.

<details><summary>Steps</summary>

1. Make sure to delete "repo" branch in your fork. You may also want to disable Actions in the repo settings.

   **Also make sure you are using the latest version of Git as many commands used here are pretty new.**

2. Do a partial clone.
    ```bash
    git clone --filter=blob:none --sparse <fork-repo-url>
    cd IReader-extensions/
    ```


## Getting help

- Join [the Discord server](https://discord.gg/HBU6zD8c5v) for online help and to ask questions while developing your extension. When doing so, please ask it in the `#app-dev` channel.
- There are some features and tricks that are not explored in this document. Refer to existing extension code for examples.

## Writing an extension

The quickest way to get started is to copy an existing extension's folder structure and renaming it as needed. We also recommend reading through a few existing extensions' code before you start.

### Setting up a new Gradle module

Each extension should reside in `sources/<lang>/<mysourcename>`. Use `sources/multisrc ` if your target source supports multiple languages or if it could support multiple sources.

The `<lang>` used in the folder inside `src` should be the major `language` part. For example, if you will be creating a `pt-BR` source, use `<lang>` here as `pt` only. Inside the source class, use the full locale string instead.

#### Extension file structure

The simplest extension structure looks like this:

```console
$ tree src/<lang>/<mysourcename>/
sources/<lang>/<mysourcename>/
├── build.gradle
├── res (option 1)
│   ├── mipmap-hdpi
│   │   └── ic_launcher.png
│   ├── mipmap-mdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxxhdpi
│   │   └── ic_launcher.png
│   └── web_hi_res_512.png
└── main
    └── assets (option 2)
	    └── icon.png
    └── src
	    └── ireader
              └── <mysourcename>
                  └── <MySourceName>.kt
```

#### build.gradle
Make sure that your new extension's `build.gradle` file follows the following structure:

```gradle
listOf("en").map { lang ->  
  Extension(  
      name = "BoxNovelCom", // no space between letters
	  versionCode = 4,  
	  libVersion = "2",  
	  lang = lang,  
	  description = "",  
	  nsfw = false,  
	  icon = DEFAULT_ICON  // if you want to use a remote image repalce `DEFAULT_ICON` with a link
  )  
}.also(::register)
```

| Field | Description |
| ----- | ----------- |
| `name` | The name of the extension. |
| `versionCode` | The extension version code. This must be a positive integer and incremented with any change to the code. |
| `libVersion` | (Optional, defaults to `1`) The version of the [extensions library](https://github.com/IReaderorg/IReader) used. |
| `lang` | the language of extension |
| `description ` | the description of extension that you want the users see in the app |
| `isNsfw` | (Optional, defaults to `false`) Flag to indicate that a source contains NSFW content. |
| `icon` | default to `DEFAULT_ICON `,if you want to use a remote image repalce `DEFAULT_ICON` with a link|
| `sourceDir` | default to ``main`` for a single extension|
| `assetsDir` | leave it blank if you you are using local res or if you are using a individual extension|
| `remoteDependencies` | if you want a dependency that is not available IReader-extension projects |

The extension's version name is generated automatically by concatenating `libVersion` and `versionCode`. With the example used above, the version would be `1.4`.

### Core dependencies

#### Extension API

Extensions rely on [extensions-lib](https://github.com/IReaderorg/IReader/tree/master/core/src/commonMain/kotlin/source), which provides some interfaces and stubs from the [app](https://github.com/IReaderorg/IReader) for compilation purposes. The actual implementations can be found [here](https://github.com/IReaderorg/IReader/tree/master/core/src/commonMain/kotlin/source). Referencing the actual implementation will help with understanding extensions' call flow.


#### Additional dependencies

If you find yourself needing additional functionality, you can add more dependencies to your `build.gradle` file.
Many of [the dependencies](https://github.com/IReaderorg/IReader/blob/master/domain/build.gradle.kts) from the main IReader app are exposed to extensions by default.

> Note that several dependencies are already exposed to all extensions via Gradle version catalog.
> To view which are available view `libs.versions.toml` under the `gradle` folder

Notice that we're using `compileOnly` instead of `implementation` if the app already contains it. You could use `implementation` instead for a new dependency, or you prefer not to rely on whatever the main app has at the expense of app size.

Note that using `compileOnly` restricts you to versions that must be compatible with those used in [the latest stable version of IReader](https://github.com/IReaderorg/IReader/releases/latest).

### Extension main class

This class should implement  one of the `Source` implementations: `HttpSource` or `ParsedHttpSource`.

| Class | Description |
| ----- | ----------- |
| `HttpSource`| For online source, where requests are made using HTTP. |
| `SourceFactory`| An Implementation of `HttpSource` that make source creation easier. |

#### Main class key variables

| Field | Description |
| ----- | ----------- |
| `name` | Name displayed in the "Sources" tab in IReader. |
| `baseUrl` | Base URL of the source without any trailing slashes. |
| `lang` | An ISO 639-1 compliant language code (two letters in lower case in most cases, but can also include the country/dialect part by using a simple dash character). |
| `id` | Identifier of your source, automatically set in `HttpSource`. It should only be manually overriden if you need to copy an existing autogenerated ID. |

### Extension call flow


####  getMangaList(sort: Listing?, page: Int)

a.k.a. the default request that app make

#### Book Search

- When the user searches inside the app, `getMangaList` will be called
- if the filters contain a Filter.Title that has a query then the you need to search for a query
    - If search functionality is not available, return `Observable.just(MangasPage(emptyList(), false))`
- `getFilterList` will be called to get all filters and filter types.
```kotlin
val query = filters.findInstance<Filter.Title>()?.value  
  
if (query != null) {  
    getBooks(query = query) // you need to implement this yourself
}
```

##### Filters

The search flow have support to filters that can be added to a `FilterList` inside the `getFilterList` method. When the user changes the filters' state, they will be passed to the `searchRequest`, and they can be iterated to create the request (by getting the `filter.state` value, where the type varies depending on the `Filter` used). You can check the filter types available [here](https://github.com/IReaderorg/IReader/blob/master/core/src/commonMain/kotlin/source/model/Filter.kt) and in the table below.

| Filter | State type | Description |
| ------ | ---------- | ----------- |
| `Filter.Note` | `String` | a note for how to use filters |
| `Filter.Select<V>` | `Int` | A select control, similar to HTML's `<select>`. Only one item can be selected, and the state is the index of the selected one. |
| `Filter.Text` | `String` | A text control, similar to HTML's `<input type="text">`. |
| `Filter.Check` | `Boolean` | A checkbox control, similar to HTML's `<input type="checkbox">`. The state is `true` if it's checked. |
| `Filter.Group<V>` | `List<V>` | A group of filters (preferentially of the same type). The state will be a `List` with all the states. |
| `Filter.Sort` | `Selection` | A control for sorting, with support for the ordering. The state indicates which item index is selected and if the sorting is `ascending`. |


#### Book Details

- When user taps on a book, `getMangaDetails` and `getChapterList` will be called and the results will be cached.
- remember to check for ``commands`` if you enable some commands.
- `fetchChapterList` is called to display the chapter list.
    - **The list should be sorted descending by the source order**.

#### Chapter

- After a chapter list for the book is fetched and the app is going to cache the data, `getChapterList` will be called.
- `ChapterInfo.dateUpload` is the [UNIX Epoch time](https://en.wikipedia.org/wiki/Unix_time) **expressed in milliseconds**.
    - If you don't pass `ChapterInfo.dateUpload` and leave it zero, the app will use the default date instead, but it's recommended to always fill it if it's available.
    - To get the time in milliseconds from a date string, you can use a `SimpleDateFormat` like in the example below.

      ```kotlin
      private fun parseDate(dateStr: String): Long {
          return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
              .getOrNull() ?: 0L
      }

      companion object {
          private val DATE_FORMATTER by lazy {
              SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
          }
      }
      ```

      Make sure you make the `SimpleDateFormat` a class constant or variable so it doesn't get recreated for every chapter. If you need to parse or format dates in manga description, create another instance since `SimpleDateFormat` is not thread-safe.

#### Chapter Pages

- When user opens a chapter, `getPageList` will be called and it will return a list of `Page`s.
- you need to parse the html to get a list of ``Pages``, for a novel its a list of `` Text``

## Writing an Multisrc Extension
Sometimes you have a common extension that is used for many languages and only some part of it need to be changed in order to create another extension for another website, in this case, we can use MultiSrc directory in ``sources/multisrc``

#### Multisrc Extension file structure

The simplest extension structure looks like this:

```console
$ tree sources/multisrc/<mysourcename>/
sources/multisrc/<mysourcename>/
├── build.gradle
├── res (option 1)
│   ├── mipmap-hdpi
│   │   └── ic_launcher.png
│   ├── mipmap-mdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxxhdpi
│   │   └── ic_launcher.png
│   └── web_hi_res_512.png
└── main ( common code that is used for othersources)
│	├── res (option 1)
│	│   ├── mipmap-hdpi
│	│   │   └── ic_launcher.png
│	│   ├── mipmap-mdpi
│	│   │   └── ic_launcher.png
│	│   ├── mipmap-xhdpi
│	│   │   └── ic_launcher.png
│	│   ├── mipmap-xxhdpi
│	│   │   └── ic_launcher.png
│	│   ├── mipmap-xxxhdpi
│	│   │   └── ic_launcher.png
│	│   └── web_hi_res_512.png
│	|
│	├── assets (option 2)
│		    └── icon.png
│    └── src
│	    └── ireader
│              └── <mysourcename>
│                  └── <MySourceName>.kt
├── res (option 1)
│   ├── mipmap-hdpi
│   │   └── ic_launcher.png
│   ├── mipmap-mdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxxhdpi
│   │   └── ic_launcher.png
│   └── web_hi_res_512.png
└── SiteA ( common code that is used for othersources)
│	├── res (option 1)
│	│   ├── mipmap-hdpi
│	│   │   └── ic_launcher.png
│	│   ├── mipmap-mdpi
│	│   │   └── ic_launcher.png
│	│   ├── mipmap-xhdpi
│	│   │   └── ic_launcher.png
│	│   ├── mipmap-xxhdpi
│	│   │   └── ic_launcher.png
│	│   ├── mipmap-xxxhdpi
│	│   │   └── ic_launcher.png
│	│   └── web_hi_res_512.png
│	|
│	├── assets (option 2)
│		    └── icon.png
│    └── src
│	    └── ireader
│              └── <SiteAName>
│                  └── <SiteAName>.kt
└── SiteB ( Site A )
|	├── res (option 1)
|	│   ├── mipmap-hdpi
|	│   │   └── ic_launcher.png
|	│   ├── mipmap-mdpi
|	│   │   └── ic_launcher.png
|	│   ├── mipmap-xhdpi
|	│   │   └── ic_launcher.png
|	│   ├── mipmap-xxhdpi
|	│   │   └── ic_launcher.png
|	│   ├── mipmap-xxxhdpi
|	│   │   └── ic_launcher.png
|	│   └── web_hi_res_512.png
|	|
|	├── assets (option 2)
|		    └── icon.png
   └── src
	    └── ireader
             └── <SiteBName>
                  └── <SiteBName>.kt
```
#### build.gradle
Make sure that your new extension's `build.gradle` file follows the following structure:

```gradle
	listOf(  
	     Extension(  
		  name = "SiteA",  
		  versionCode = 1,  
		  libVersion = "2",  
		  lang = "en",  
		  description = "",  
		  nsfw = false,  
		  icon = DEFAULT_ICON,  
		  assetsDir = "multisrc/<mysourcename>/SiteA/assets",  
		  sourceDir = "SiteA",  
		  ),  
		  Extension(  
		        name = "SiteB",  
		  versionCode = 1,  
		  libVersion = "2",  
		  lang = "ar",  
		  description = "",  
		  nsfw = false,  
		  icon = DEFAULT_ICON,  
		  assetsDir = "multisrc/<mysourcename>/SiteB/assets",  
		  sourceDir = "SiteB",  
		  ),  
		).also(::register)
```
<B> Note:</b> if you want to test your extension you <B>must comment other extension in build.gradle.kts</B> 

## Running
You can run it directly in android studio, after selecting it in ``Run/Debug Configuration``


## Building

APKs can be created in Android Studio via `Build > Build Bundle(s) / APK(s) > Build APK(s)` or `Build > Generate Signed Bundle / APK`.

## Submitting the changes

When you feel confident about your changes, submit a new Pull Request so your code can be reviewed and merged if it's approved. We encourage following a [GitHub Standard Fork & Pull Request Workflow](https://gist.github.com/Chaser324/ce0505fbed06b947d962) and following the good practices of the workflow, such as not commiting directly to `master`: always create a new branch for your changes.

If you are more comfortable about using Git GUI-based tools, you can refer to [this guide](https://learntodroid.com/how-to-use-git-and-github-in-android-studio/) about the Git integration inside Android Studio, specifically the "How to Contribute to an to Existing Git Repository in Android Studio" section of the guide.

Please **do test your changes by compiling it through Android Studio** before submitting it. Also make sure to follow the PR checklist available in the PR body field when creating a new PR. As a reference, you can find it below.

### Pull Request checklist

- Update `VersionCode` value in `build.gradle` for individual extensions
- Add the `isNsfw = true` flag in `build.gradle` when appropriate
- Explicitly kept the `id` if a source's name or language were changed
- Test the modifications by compiling and running the extension through Android Studio
