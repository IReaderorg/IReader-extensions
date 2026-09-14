package ireader.mknov

/**
 * WOFF2 container → cmap/post → character-substitution decoder.
 *
 * mknov.com obfuscates chapter text: every chapter ships a WOFF2 font
 * (`/fonts/protected-font-N.woff2`) whose glyphs are *named after the real
 * letters* (e.g. ciphertext `ة` U+0629 maps to glyph `uni0624` → real `ؤ`).
 * Decrypting a chapter = substitute each cipher char via the font's cmap and
 * post glyph-name tables.
 *
 * Pipeline (all verified byte-exact against fontTools):
 *
 * 1. **WOFF2 container** — 48-byte header, then a variable-length table
 *    directory (UIntBase128). `flags & 0x3F` indexes a fixed KNOWN_TAGS list
 *    (63 entries; index 63 ⇒ an explicit 4-byte tag follows). Whether a table
 *    is *transformed* follows the transform-version bits `flags >> 6`:
 *      - for `glyf`/`loca`: version == 3 ⇒ null transform
 *      - for all other tables: version == 0 ⇒ null transform
 *    A transformed table stores its *stream length* (transformLength) after
 *    origLength; a null-transform table contributes its origLength. Tables are
 *    laid out contiguously in the single Brotli stream in directory order.
 *
 * 2. **Brotli** — the stream starts right after the directory; a single call
 *    to [Brotli.decompress] yields the concatenated table bytes.
 *
 * 3. **cmap** — parse the format-4 (platform 3/encoding 1, Windows) and
 *    format-12 (platform 0/encoding 4, Unicode) subtables → `cp → glyphId`.
 *
 * 4. **post (format 2.0)** — `glyphId → glyphName`: indices > 257 index the
 *    custom Pascal-string array; indices ≤ 257 are the fixed Macintosh names
 *    (never `uniXXXX`, so irrelevant for substitution).
 *
 * 5. **substitution map** — for each `(cp, glyphName)`, when the name is
 *    `uniXXXX`, parse XXXX as hex → real codepoint. `cp → real`.
 */
internal object Woff2Cmap {

    private class TableInfo(
        val tag: String,
        val origLength: Int,
        val transformLength: Int?,
    )

    /** The WOFF2 spec's 63 known table tags (kept exact as fontTools' list). */
    private val KNOWN_TAGS = arrayOf(
        "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2", "post", "cvt ", "fpgm",
        "glyf", "loca", "prep", "CFF ", "VORG", "EBDT", "EBLC", "gasp", "hdmx", "kern",
        "LTSH", "PCLT", "VDMX", "vhea", "vmtx", "BASE", "GDEF", "GPOS", "GSUB", "EBSC",
        "JSTF", "MATH", "CBDT", "CBLC", "COLR", "CPAL", "SVG ", "sbix", "acnt", "avar",
        "bdat", "bloc", "bsln", "cvar", "fdsc", "feat", "fmtx", "fvar", "gvar", "hsty",
        "just", "lcar", "mort", "morx", "opbd", "prop", "trak", "Zapf", "Silf", "Glat",
        "Gloc", "Feat", "Sill",
    )

    /**
     * Build the `cipherCp → realCp` substitution map from a WOFF2 byte array.
     * Returns an empty map when the font isn't the expected shape (or brotli
     * / cmap / post parsing fails) — callers treat an empty map as "decrypt
     * with no substitution".
     */
    fun substitutionMap(woff2: ByteArray): Map<Int, Int> {
        val tables = runCatching { parseDirectory(woff2) }.getOrNull() ?: return emptyMap()
        val cmapTag = tables.firstOrNull { it.tag == "cmap" } ?: return emptyMap()
        val postTag = tables.firstOrNull { it.tag == "post" } ?: return emptyMap()

        // Brotli stream offset = 48 + directory bytes.
        val streamStart = 48 + directorySize(woff2, tables.size)
        val totalCompressed = readU32(woff2, 20)
        if (streamStart + totalCompressed > woff2.size) return emptyMap()

        val compressed = woff2.copyOfRange(streamStart, streamStart + totalCompressed)
        val data = runCatching { Brotli.decompress(compressed) }.getOrNull() ?: return emptyMap()

        // Locate tables in the decompressed stream (accumulate by stream length).
        val cmap = tableAt(data, tables, cmapTag) ?: return emptyMap()
        val post = tableAt(data, tables, postTag) ?: return emptyMap()

        val cpToGid = runCatching { parseCmap(cmap) }.getOrNull() ?: return emptyMap()
        val gidToName = runCatching { parsePost(post) }.getOrNull() ?: return emptyMap()

        val map = HashMap<Int, Int>()
        for ((cp, gid) in cpToGid) {
            val name = gidToName[gid] ?: continue
            if (!name.startsWith("uni")) continue
            val stem = name.substringBefore('.')
            val hexPart = stem.removePrefix("uni")
            if (hexPart.length != 4 && hexPart.length != 6) continue
            val real = parseHex(hexPart) ?: continue
            if (real <= 0x10FFFF) map[cp] = real
        }
        return map
    }

    /** Diagnostic: report which pipeline stage fails for a given font. */
    internal fun debug(woff2: ByteArray): String {
        val sb = StringBuilder()
        val tables = runCatching { parseDirectory(woff2) }.getOrNull()
        if (tables == null) return "dir parse FAILED"
        sb.append("nTables=${tables.size} tags=${tables.map { it.tag }}")
        val streamStart = 48 + directorySize(woff2, tables.size)
        val totalCompressed = readU32(woff2, 20)
        sb.append(" streamStart=$streamStart totalComp=$totalCompressed sz=${woff2.size}")
        if (streamStart + totalCompressed > woff2.size) return sb.append(" BAD bounds").toString()
        val compressed = woff2.copyOfRange(streamStart, streamStart + totalCompressed)
        val data = runCatching { Brotli.decompress(compressed) }.getOrNull()
        if (data == null) {
            // distinguish throw vs wrong result
            val threw = runCatching { Brotli.decompress(compressed) }.exceptionOrNull()
            var trace = ""
            if (threw != null) {
                val sw = StringBuilder()
                for (el in threw.stackTrace) {
                    if (el.fileName != null && (el.fileName == "Brotli.kt" || el.fileName == "Woff2Cmap.kt")) {
                        sw.append(el.methodName).append(":").append(el.lineNumber).append(" <- ")
                    }
                }
                trace = sw.toString()
            }
            return sb.append(" brotli FAILED(${threw?.javaClass?.simpleName}: ${threw?.message} @$trace)").toString()
        }
        sb.append(" decompressed=${data.size}")
        val cmapTag = tables.firstOrNull { it.tag == "cmap" }
        val postTag = tables.firstOrNull { it.tag == "post" }
        if (cmapTag != null) {
            val cmap = tableAt(data, tables, cmapTag)
            if (cmap != null) {
                val cpToGid = runCatching { parseCmap(cmap) }.getOrNull()
                sb.append(" cmapLen=${cmap.size} cpToGid=${cpToGid?.size}")
            } else sb.append(" cmap NOT in stream")
        }
        if (postTag != null) {
            val post = tableAt(data, tables, postTag)
            if (post != null) {
                val gidToName = runCatching { parsePost(post) }.getOrNull()
                sb.append(" postLen=${post.size} gidToName=${gidToName?.size}")
            } else sb.append(" post NOT in stream")
        }
        return sb.toString()
    }

    // ── WOFF2 container ────────────────────────────────────────────────

    private fun parseDirectory(data: ByteArray): List<TableInfo> {
        if (data.size < 48) return emptyList()
        if (!isWoff2(data)) return emptyList()
        val numTables = readU16(data, 12)
        var off = 48
        val tables = ArrayList<TableInfo>(numTables)
        for (i in 0 until numTables) {
            val flags = data[off++].toInt() and 0xFF
            val idx = flags and 0x3F
            val tag: String
            if (idx == 0x3F) {
                tag = String(data, off, 4, Charsets.US_ASCII)
                off += 4
            } else {
                if (idx >= KNOWN_TAGS.size) return emptyList()
                tag = KNOWN_TAGS[idx]
            }
            val (origLength, o1) = readUIntBase128(data, off)
            off = o1
            var transformLength: Int? = null
            if (isTransformed(flags, tag)) {
                val (tlen, o2) = readUIntBase128(data, off)
                off = o2
                transformLength = tlen
            }
            tables.add(TableInfo(tag, origLength, transformLength))
        }
        return tables
    }

    private fun directorySize(data: ByteArray, numTables: Int): Int {
        // Re-walk to find where the directory ends: entry after entry.
        var off = 48
        for (i in 0 until numTables) {
            val flags = data[off++].toInt() and 0xFF
            val idx = flags and 0x3F
            if (idx == 0x3F) off += 4
            val (_, o1) = readUIntBase128(data, off)
            off = o1
            if (isTransformed(flags, KNOWN_TAGS.getOrNull(idx) ?: "")) {
                val (_, o2) = readUIntBase128(data, off)
                off = o2
            }
        }
        return off - 48
    }

    private fun isWoff2(data: ByteArray): Boolean =
        data[0].toInt() == 'w'.code && data[1].toInt() == 'O'.code &&
            data[2].toInt() == 'F'.code && data[3].toInt() == '2'.code

    private fun isTransformed(flags: Int, tag: String): Boolean {
        val version = flags ushr 6
        return if (tag == "glyf" || tag == "loca") version != 3 else version != 0
    }

    /** Returns the table's bytes at its position in the decompressed stream. */
    private fun tableAt(data: ByteArray, tables: List<TableInfo>, target: TableInfo): ByteArray? {
        var acc = 0
        for (t in tables) {
            val len = t.transformLength ?: t.origLength
            if (t === target) {
                return if (acc + len <= data.size) data.copyOfRange(acc, acc + len) else null
            }
            acc += len
        }
        return null
    }

    // ── UIntBase128 ─────────────────────────────────────────────────────

    private fun readUIntBase128(data: ByteArray, off: Int): Pair<Int, Int> {
        var result = 0
        var o = off
        for (i in 0 until 5) {
            val b = data[o++].toInt() and 0xFF
            if (i == 4 && (b and 0xF0) != 0) throw IllegalArgumentException("u128 overflow")
            result = (result shl 7) or (b and 0x7F)
            if (b and 0x80 == 0) return result to o
        }
        throw IllegalArgumentException("u128 too long")
    }

    // ── cmap ────────────────────────────────────────────────────────────

    private fun parseCmap(cmap: ByteArray): Map<Int, Int> {
        val numSubtables = readU16(cmap, 2)
        val map = HashMap<Int, Int>()
        for (i in 0 until numSubtables) {
            val base = 4 + 8 * i
            val platform = readU16(cmap, base)
            val encoding = readU16(cmap, base + 2)
            val offset = readU32(cmap, base + 4)
            if (offset + 2 > cmap.size) continue
            val format = readU16(cmap, offset)
            when {
                format == 12 && platform == 0 -> parseFormat12(cmap, offset, map)
                format == 4 && platform == 3 && encoding == 1 -> parseFormat4(cmap, offset, map)
            }
        }
        return map
    }

    private fun parseFormat12(cmap: ByteArray, off: Int, map: MutableMap<Int, Int>) {
        val nGroups = readU32(cmap, off + 12)
        for (g in 0 until nGroups) {
            val base = off + 16 + 12 * g
            if (base + 12 > cmap.size) break
            val start = readU32(cmap, base)
            val end = readU32(cmap, base + 4)
            val startGid = readU32(cmap, base + 8)
            for (cp in start until end + 1) {
                map[cp] = startGid + (cp - start)
            }
        }
    }

    private fun parseFormat4(cmap: ByteArray, off: Int, map: MutableMap<Int, Int>) {
        val segCountX2 = readU16(cmap, off + 6)
        val segCount = segCountX2 / 2
        val endCodesBase = off + 14
        val startCodesBase = off + 14 + segCountX2 + 2
        val idDeltasBase = off + 14 + 2 * segCountX2 + 2
        val idRangeOffsetsBase = off + 14 + 3 * segCountX2 + 2

        for (s in 0 until segCount) {
            val sc = readU16(cmap, startCodesBase + 2 * s)
            val ec = readU16(cmap, endCodesBase + 2 * s)
            val idDelta = readI16(cmap, idDeltasBase + 2 * s)
            val idRangeOffset = readU16(cmap, idRangeOffsetsBase + 2 * s)
            for (cp in sc until ec + 1) {
                if (cp == 0xFFFF) continue
                var gid: Int
                if (idRangeOffset == 0) {
                    gid = (cp + idDelta) and 0xFFFF
                } else {
                    // address of the glyph array entry for cp.
                    val addr = idRangeOffsetsBase + 2 * s + idRangeOffset + 2 * (cp - sc)
                    if (addr + 2 > cmap.size) continue
                    gid = readU16(cmap, addr)
                    if (gid != 0) gid = (gid + idDelta) and 0xFFFF
                }
                if (gid != 0) map[cp] = gid
            }
        }
    }

    // ── post ────────────────────────────────────────────────────────────

    private fun parsePost(post: ByteArray): Map<Int, String> {
        if (readU32(post, 0) != 0x00020000u.toInt()) return emptyMap() // format 2.0
        val numGlyphs = readU16(post, 32)
        val idxBase = 34
        var maxIndex = 0
        val indices = IntArray(numGlyphs)
        for (g in 0 until numGlyphs) {
            val i = readU16(post, idxBase + 2 * g)
            indices[g] = i
            if (i > maxIndex) maxIndex = i
        }
        // custom names: exactly maxIndex-257 Pascal strings (matches fontTools unpackPStrings).
        var stringsOff = idxBase + 2 * numGlyphs
        val nStrings = maxIndex - 257
        val names = ArrayList<String>(maxOf(nStrings, 0))
        for (i in 0 until nStrings) {
            if (stringsOff >= post.size) {
                names.add("")
                stringsOff++
                continue
            }
            val length = post[stringsOff++].toInt() and 0xFF
            if (stringsOff + length > post.size) {
                names.add("")
                stringsOff = post.size
                continue
            }
            names.add(String(post, stringsOff, length, Charsets.US_ASCII))
            stringsOff += length
        }
        val map = HashMap<Int, String>()
        for (g in 0 until numGlyphs) {
            val i = indices[g]
            if (i > 257 && (i - 258) < names.size) map[g] = names[i - 258]
        }
        return map
    }

    // ── endian helpers ─────────────────────────────────────────────────

    private fun readU16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun readI16(b: ByteArray, off: Int): Int {
        val u = readU16(b, off)
        return if (u and 0x8000 != 0) u - 0x10000 else u
    }

    private fun readU32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)

    private fun parseHex(s: String): Int? {
        if (s.isEmpty()) return null
        var acc = 0
        for (c in s) {
            val d = when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> return null
            }
            acc = (acc shl 4) or d
        }
        return acc
    }
}