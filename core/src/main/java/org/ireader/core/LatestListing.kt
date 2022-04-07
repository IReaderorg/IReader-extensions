package org.ireader.core

import tachiyomi.source.model.Filter
import tachiyomi.source.model.Listing

class LatestListing : Listing("Latest")
class PopularListing : Listing("Popular")
class SearchListing : Listing("Search:")


class DetailParse : Listing("DetailParse")
class ChaptersParse : Listing("ChapterListParse")
class ChapterParse : Listing("ChapterParse")

const val PARSE_CONTENT = "PARSE_CONTENT"
const val PARSE_DETAIL = "PARSE_DETAIL"
const val PARSE_CHAPTERS = "PARSE_CHAPTERS"
