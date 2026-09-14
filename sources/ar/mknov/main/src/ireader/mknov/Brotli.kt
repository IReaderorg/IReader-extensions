package ireader.mknov

/**
 * Pure Kotlin Brotli decompressor — faithful port of Google's reference Java
 * decoder (org.brotli.dec, MIT licensed).
 *
 * Stripped to an in-memory byte[] API. The static LZ77 dictionary is omitted:
 * WOFF2 font streams (quality=11, window=22) never reference it because the
 * ring buffer (≥ window size, always ≥ 4 MiB for WOFF2) covers the whole
 * decompressed font table. A dictionary transform (distance > maxDistance)
 * throws — it would only occur for non-WOFF2 streams.
 *
 * The context lookup table lives in [BrotliLookup].
 */
internal object Brotli {

    private const val CODE_LENGTH_REPEAT_CODE = 16
    private const val NUM_LITERAL_CODES = 256
    private const val NUM_INSERT_AND_COPY_CODES = 704
    private const val NUM_BLOCK_LENGTH_CODES = 26
    private const val LITERAL_CONTEXT_BITS = 6
    private const val DISTANCE_CONTEXT_BITS = 2
    private const val HUFFMAN_TABLE_BITS = 8
    private const val HUFFMAN_TABLE_MASK = 0xFF
    private const val CODE_LENGTH_CODES = 18
    private const val NUM_DISTANCE_SHORT_CODES = 16
    private const val DEFAULT_CODE_LENGTH = 8

    private const val MAX_LENGTH = 15

    // Register-names for the running states (mirror RunningState.java).
    private const val ST_BLOCK_START = 1
    private const val ST_COMPRESSED_BLOCK_START = 2
    private const val ST_MAIN_LOOP = 3
    private const val ST_READ_METADATA = 4
    private const val ST_COPY_UNCOMPRESSED = 5
    private const val ST_INSERT_LOOP = 6
    private const val ST_COPY_LOOP = 7
    private const val ST_COPY_WRAP_BUFFER = 8
    private const val ST_TRANSFORM = 9
    private const val ST_FINISHED = 10
    private const val ST_WRITE = 12

    private const val HUFFMAN_MAX_TABLE_SIZE = 1080

    // Word-transform types (mirror WordTransformType.java).
    private const val IDENTITY = 0
    private const val OMIT_LAST_1 = 1
    private const val OMIT_LAST_2 = 2
    private const val OMIT_LAST_3 = 3
    private const val OMIT_LAST_4 = 4
    private const val OMIT_LAST_5 = 5
    private const val OMIT_LAST_6 = 6
    private const val OMIT_LAST_7 = 7
    private const val OMIT_LAST_8 = 8
    private const val OMIT_LAST_9 = 9
    private const val UPPERCASE_FIRST = 10
    private const val UPPERCASE_ALL = 11
    private const val OMIT_FIRST_1 = 12
    private const val OMIT_FIRST_2 = 13
    private const val OMIT_FIRST_3 = 14
    private const val OMIT_FIRST_4 = 15
    private const val OMIT_FIRST_5 = 16
    private const val OMIT_FIRST_6 = 17
    private const val OMIT_FIRST_7 = 18
    private const val OMIT_FIRST_8 = 19
    private const val OMIT_FIRST_9 = 20

    private val CODE_LENGTH_CODE_ORDER = intArrayOf(
        1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15,
    )
    private val DISTANCE_SHORT_CODE_INDEX_OFFSET = intArrayOf(
        3, 2, 1, 0, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2,
    )
    private val DISTANCE_SHORT_CODE_VALUE_OFFSET = intArrayOf(
        0, 0, 0, 0, -1, 1, -2, 2, -3, 3, -1, 1, -2, 2, -3, 3,
    )
    private val FIXED_TABLE = intArrayOf(
        0x020000, 0x020004, 0x020003, 0x030002, 0x020000, 0x020004,
        0x020003, 0x040001, 0x020000, 0x020004, 0x020003, 0x030002,
        0x020000, 0x020004, 0x020003, 0x040005,
    )

    // Prefix-code value tables (mirror Prefix.java).
    private val BLOCK_LENGTH_OFFSET = intArrayOf(
        1, 5, 9, 13, 17, 25, 33, 41, 49, 65, 81, 97, 113, 145, 177, 209,
        241, 305, 369, 497, 753, 1265, 2289, 4337, 8433, 16625,
    )
    private val BLOCK_LENGTH_N_BITS = intArrayOf(
        2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 7, 8, 9, 10, 11, 12, 13, 24,
    )
    private val INSERT_LENGTH_OFFSET = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 8, 10, 14, 18, 26, 34, 50, 66, 98,
        130, 194, 322, 578, 1090, 2114, 6210, 22594,
    )
    private val INSERT_LENGTH_N_BITS = intArrayOf(
        0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 12, 14, 24,
    )
    private val COPY_LENGTH_OFFSET = intArrayOf(
        2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 18, 22, 30, 38, 54,
        70, 102, 134, 198, 326, 582, 1094, 2118,
    )
    private val COPY_LENGTH_N_BITS = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 24,
    )
    private val INSERT_RANGE_LUT = intArrayOf(0, 0, 8, 8, 0, 16, 8, 16, 16)
    private val COPY_RANGE_LUT = intArrayOf(0, 8, 0, 8, 16, 0, 16, 8, 16)

    /**
     * Growable byte buffer — multiplatform replacement for
     * java.io.ByteArrayOutputStream (not available in Kotlin/JS).
     */
    internal class ByteBuffer {
        private var buf = ByteArray(4096)
        private var size = 0

        fun write(src: ByteArray, from: Int, n: Int) {
            ensure(size + n)
            for (i in 0 until n) buf[size + i] = src[from + i]
            size += n
        }

        fun writeByte(b: Byte) {
            ensure(size + 1)
            buf[size++] = b
        }

        fun size() = size

        private fun ensure(cap: Int) {
            if (cap > buf.size) {
                var newSize = buf.size * 2
                while (newSize < cap) newSize *= 2
                buf = buf.copyOf(newSize)
            }
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    /** In-memory bit reader (mirrors BitReader.java). */
    internal class BitReader(val data: ByteArray) {
        var pos = 0
        var accumulator = 0L
        var bitOffset = 64

        fun fillBitWindow() {
            if (bitOffset >= 32) {
                var v: Long = 0
                val rem = data.size - pos
                if (rem >= 4) {
                    v = (data[pos].toLong() and 0xFF) or
                        ((data[pos + 1].toLong() and 0xFF) shl 8) or
                        ((data[pos + 2].toLong() and 0xFF) shl 16) or
                        ((data[pos + 3].toLong() and 0xFF) shl 24)
                    pos += 4
                } else {
                    var k = 0
                    while (k < 4) {
                        if (k < rem) v = v or ((data[pos + k].toLong() and 0xFF) shl (k * 8))
                        k++
                    }
                    pos += rem
                }
                accumulator = (v shl 32) or (accumulator ushr 32)
                bitOffset -= 32
            }
        }

        fun readBits(n: Int): Int {
            fillBitWindow()
            val result = (accumulator ushr bitOffset).toInt() and ((1 shl n) - 1)
            bitOffset += n
            return result
        }
    }

    // ── Huffman table construction (mirror Huffman.java) ─────────────────
    private fun getNextKey(key: Int, len: Int): Int {
        var step = 1 shl (len - 1)
        var k = key
        while ((k and step) != 0) step = step shr 1
        return (k and (step - 1)) + step
    }

    private fun replicateValue(table: IntArray, offset: Int, step: Int, end: Int, item: Int) {
        var e = end - step
        table[offset + e] = item
        while (e > 0) {
            e -= step
            table[offset + e] = item
        }
    }

    private fun nextTableBitSize(count: IntArray, len: Int, rootBits: Int): Int {
        var left = 1 shl (len - rootBits)
        var l = len
        while (l < MAX_LENGTH) {
            left -= count[l]
            if (left <= 0) break
            l++
            left = left shl 1
        }
        return l - rootBits
    }

    private fun buildHuffmanTable(
        rootTable: IntArray, tableOffset: Int, rootBits: Int,
        codeLengths: IntArray, codeLengthsSize: Int,
    ) {
        val sorted = IntArray(codeLengthsSize)
        val count = IntArray(MAX_LENGTH + 1)
        val offset = IntArray(MAX_LENGTH + 1)

        for (sym in 0 until codeLengthsSize) count[codeLengths[sym]]++

        offset[1] = 0
        for (l in 1 until MAX_LENGTH) offset[l + 1] = offset[l] + count[l]

        for (sym in 0 until codeLengthsSize) {
            if (codeLengths[sym] != 0) sorted[offset[codeLengths[sym]]++] = sym
        }

        var tableBits = rootBits
        var tableSize = 1 shl tableBits
        var totalSize = tableSize

        if (offset[MAX_LENGTH] == 1) {
            val single = sorted[0]
            for (key in 0 until totalSize) rootTable[tableOffset + key] = single
            return
        }

        var key = 0
        var symbol = 0
        var step = 2
        for (l in 1..rootBits) {
            while (count[l] > 0) {
                replicateValue(rootTable, tableOffset + key, step, tableSize, l shl 16 or sorted[symbol++])
                count[l]--
                key = getNextKey(key, l)
            }
            step = step shl 1
        }

        val mask = totalSize - 1
        var low = -1
        var currentOffset = tableOffset
        step = 2
        for (l in (rootBits + 1)..MAX_LENGTH) {
            while (count[l] > 0) {
                if ((key and mask) != low) {
                    currentOffset += tableSize
                    tableBits = nextTableBitSize(count, l, rootBits)
                    tableSize = 1 shl tableBits
                    totalSize += tableSize
                    low = key and mask
                    rootTable[tableOffset + low] =
                        (tableBits + rootBits) shl 16 or (currentOffset - tableOffset - low)
                }
                replicateValue(
                    rootTable, currentOffset + (key ushr rootBits), step, tableSize,
                    (l - rootBits) shl 16 or sorted[symbol++],
                )
                count[l]--
                key = getNextKey(key, l)
            }
            step = step shl 1
        }
    }

    private fun readSymbol(table: IntArray, offset: Int, br: BitReader): Int {
        br.fillBitWindow()
        val v = (br.accumulator ushr br.bitOffset).toInt()
        var o = offset + (v and HUFFMAN_TABLE_MASK)
        val bits = table[o] ushr 16
        val sym = table[o] and 0xFFFF
        if (bits <= HUFFMAN_TABLE_BITS) {
            br.bitOffset += bits
            return sym
        }
        o += sym + ((v and ((1 shl bits) - 1)) ushr HUFFMAN_TABLE_BITS)
        br.bitOffset += (table[o] ushr 16) + HUFFMAN_TABLE_BITS
        return table[o] and 0xFFFF
    }

    // ── Prefix helpers ──────────────────────────────────────────────────
    private fun decodeVarLenUnsignedByte(br: BitReader): Int {
        if (br.readBits(1) != 0) {
            val n = br.readBits(3)
            return if (n == 0) 1 else br.readBits(n) + (1 shl n)
        }
        return 0
    }

    private fun readBlockLength(table: IntArray, offset: Int, br: BitReader): Int {
        br.fillBitWindow()
        val code = readSymbol(table, offset, br)
        val n = BLOCK_LENGTH_N_BITS[code]
        return BLOCK_LENGTH_OFFSET[code] + br.readBits(n)
    }

    private fun translateShortCodes(code: Int, ringBuffer: IntArray, index: Int): Int {
        if (code < NUM_DISTANCE_SHORT_CODES) {
            var idx = index + DISTANCE_SHORT_CODE_INDEX_OFFSET[code]
            idx = idx and 3
            return ringBuffer[idx] + DISTANCE_SHORT_CODE_VALUE_OFFSET[code]
        }
        return code - NUM_DISTANCE_SHORT_CODES + 1
    }

    private fun readHuffmanCodeLengths(
        codeLengthCodeLengths: IntArray, numSymbols: Int, codeLengths: IntArray, br: BitReader,
    ) {
        var symbol = 0
        var prevCodeLen = DEFAULT_CODE_LENGTH
        var repeat = 0
        var repeatCodeLen = 0
        var space = 32768
        val table = IntArray(32)
        buildHuffmanTable(table, 0, 5, codeLengthCodeLengths, CODE_LENGTH_CODES)

        while (symbol < numSymbols && space > 0) {
            br.fillBitWindow()
            val p = (br.accumulator ushr br.bitOffset).toInt() and 31
            br.bitOffset += table[p] ushr 16
            val codeLen = table[p] and 0xFFFF
            if (codeLen < CODE_LENGTH_REPEAT_CODE) {
                repeat = 0
                codeLengths[symbol++] = codeLen
                if (codeLen != 0) {
                    prevCodeLen = codeLen
                    space -= 32768 ushr codeLen
                }
            } else {
                val extraBits = codeLen - 14
                var newLen = 0
                if (codeLen == CODE_LENGTH_REPEAT_CODE) newLen = prevCodeLen
                if (repeatCodeLen != newLen) {
                    repeat = 0
                    repeatCodeLen = newLen
                }
                val oldRepeat = repeat
                if (repeat > 0) {
                    repeat -= 2
                    repeat = repeat shl extraBits
                }
                repeat += br.readBits(extraBits) + 3
                val repeatDelta = repeat - oldRepeat
                if (symbol + repeatDelta > numSymbols) {
                    throw IllegalArgumentException("symbol + repeatDelta > numSymbols")
                }
                for (i in 0 until repeatDelta) codeLengths[symbol++] = repeatCodeLen
                if (repeatCodeLen != 0) space -= repeatDelta shl (15 - repeatCodeLen)
            }
        }
        if (space != 0) throw IllegalArgumentException("Unused space")
        for (i in symbol until numSymbols) codeLengths[i] = 0
    }

    private fun readHuffmanCode(
        alphabetSize: Int, table: IntArray, offset: Int, br: BitReader,
    ) {
        var ok = true
        val codeLengths = IntArray(alphabetSize)
        val simpleCodeOrSkip = br.readBits(2)
        if (simpleCodeOrSkip == 1) {
            var maxBitsCounter = alphabetSize - 1
            var maxBits = 0
            while (maxBitsCounter != 0) {
                maxBitsCounter = maxBitsCounter shr 1
                maxBits++
            }
            val numSymbols = br.readBits(2) + 1
            val symbols = IntArray(4)
            for (i in 0 until numSymbols) {
                symbols[i] = br.readBits(maxBits) % alphabetSize
                codeLengths[symbols[i]] = 2
            }
            codeLengths[symbols[0]] = 1
            when (numSymbols) {
                1 -> {}
                2 -> {
                    ok = symbols[0] != symbols[1]
                    codeLengths[symbols[1]] = 1
                }
                3 -> {
                    ok = symbols[0] != symbols[1] && symbols[0] != symbols[2] &&
                        symbols[1] != symbols[2]
                }
                else -> {
                    ok = symbols[0] != symbols[1] && symbols[0] != symbols[2] &&
                        symbols[0] != symbols[3] && symbols[1] != symbols[2] &&
                        symbols[1] != symbols[3] && symbols[2] != symbols[3]
                    if (br.readBits(1) == 1) {
                        codeLengths[symbols[2]] = 3
                        codeLengths[symbols[3]] = 3
                    } else {
                        codeLengths[symbols[0]] = 2
                    }
                }
            }
        } else {
            val codeLengthCodeLengths = IntArray(CODE_LENGTH_CODES)
            var space = 32
            var numCodes = 0
            var i = simpleCodeOrSkip
            while (i < CODE_LENGTH_CODES && space > 0) {
                val codeLenIdx = CODE_LENGTH_CODE_ORDER[i]
                br.fillBitWindow()
                val p = (br.accumulator ushr br.bitOffset).toInt() and 15
                br.bitOffset += FIXED_TABLE[p] ushr 16
                val v = FIXED_TABLE[p] and 0xFFFF
                codeLengthCodeLengths[codeLenIdx] = v
                if (v != 0) {
                    space -= 32 ushr v
                    numCodes++
                }
                i++
            }
            ok = numCodes == 1 || space == 0
            readHuffmanCodeLengths(codeLengthCodeLengths, alphabetSize, codeLengths, br)
        }
        if (!ok) throw IllegalArgumentException("Can't readHuffmanCode")
        buildHuffmanTable(table, offset, HUFFMAN_TABLE_BITS, codeLengths, alphabetSize)
    }

    private fun inverseMoveToFrontTransform(v: ByteArray, vLen: Int) {
        val mtf = IntArray(256) { it }
        for (i in 0 until vLen) {
            val index = v[i].toInt() and 0xFF
            v[i] = mtf[index].toByte()
            if (index != 0) {
                val value = mtf[index]
                var k = index
                while (k > 0) {
                    mtf[k] = mtf[k - 1]
                    k--
                }
                mtf[0] = value
            }
        }
    }

    private fun decodeContextMap(contextMapSize: Int, contextMap: ByteArray, br: BitReader): Int {
        br.fillBitWindow()
        val numTrees = decodeVarLenUnsignedByte(br) + 1
        if (numTrees == 1) {
            for (i in 0 until contextMapSize) contextMap[i] = 0
            return numTrees
        }
        val useRleForZeros = br.readBits(1) == 1
        var maxRunLengthPrefix = 0
        if (useRleForZeros) maxRunLengthPrefix = br.readBits(4) + 1
        val table = IntArray(HUFFMAN_MAX_TABLE_SIZE)
        readHuffmanCode(numTrees + maxRunLengthPrefix, table, 0, br)
        var i = 0
        while (i < contextMapSize) {
            br.fillBitWindow()
            val code = readSymbol(table, 0, br)
            if (code == 0) {
                contextMap[i] = 0
                i++
            } else if (code <= maxRunLengthPrefix) {
                var reps = (1 shl code) + br.readBits(code)
                while (reps != 0) {
                    if (i >= contextMapSize) throw IllegalArgumentException("Corrupted context map")
                    contextMap[i] = 0
                    i++
                    reps--
                }
            } else {
                contextMap[i] = (code - maxRunLengthPrefix).toByte()
                i++
            }
        }
        if (br.readBits(1) == 1) inverseMoveToFrontTransform(contextMap, contextMapSize)
        return numTrees
    }

    // ── State holder ────────────────────────────────────────────────────
    private class State(val br: BitReader, val ring: ByteArray) {
        var pos = 0
        val ringBufferMask = ring.size - 1

        // metablock header
        var inputEnd = false
        var isUncompressed = false
        var isMetadata = false
        var metaBlockLength = 0

        // block types
        val blockTypeTrees = IntArray(3 * HUFFMAN_MAX_TABLE_SIZE)
        val blockLenTrees = IntArray(3 * HUFFMAN_MAX_TABLE_SIZE)
        val blockLength = intArrayOf(1 shl 28, 1 shl 28, 1 shl 28)
        val numBlockTypes = intArrayOf(1, 1, 1)
        val blockTypeRb = intArrayOf(1, 0, 1, 0, 1, 0)
        val distRb = intArrayOf(16, 15, 11, 4)
        var distRbIdx = 0

        // context / distances
        var contextModes: ByteArray = ByteArray(0)
        var contextMap: ByteArray = ByteArray(0)
        var contextMapSlice = 0
        var literalTreeIndex = 0
        var literalTree = 0
        var trivialLiteralContext = true
        var contextLookupOffset1 = 0
        var contextLookupOffset2 = 0
        var treeCommandOffset = 0
        var distContextMap: ByteArray = ByteArray(0)
        var distContextMapSlice = 0
        var distancePostfixBits = 0
        var numDirectDistanceCodes = 0
        var distancePostfixMask = 0

        var maxBackwardDistance: Int = ring.size - 16

        // max readable distance (mirrors reference DECODE state.maxDistance)
        var maxDistance: Int = 0

        // insert/copy/distance
        var j = 0
        var insertLength = 0
        var copyLength = 0
        var distanceCode = 0
        var distance = 0
        var copyDst = 0

        // huffman groups
        var h0codes: IntArray = IntArray(0)
        var h0trees: IntArray = IntArray(0)
        var h1codes: IntArray = IntArray(0)
        var h1trees: IntArray = IntArray(0)
        var h2codes: IntArray = IntArray(0)
        var h2trees: IntArray = IntArray(0)

        var runningState = ST_BLOCK_START
        var nextRunningState = ST_BLOCK_START

        var bytesToWrite = 0
        var bytesWritten = 0
    }

    // ── Public entry point ──────────────────────────────────────────────

    /** Decompress a Brotli stream into the target byte array (or grow it). */
    fun decompress(data: ByteArray): ByteArray {
        val br = BitReader(data)

        // Window bits
        val windowBits: Int
        if (br.readBits(1) == 0) {
            windowBits = 16
        } else {
            var n = br.readBits(3)
            windowBits = if (n != 0) 17 + n else {
                n = br.readBits(3)
                if (n != 0) 8 + n else 17
            }
        }
        if (windowBits == 9) throw IllegalArgumentException("Invalid windowBits code")

        // Ring buffer of the window size; for a single-metablock WOFF2 stream the
        // decompressed data always fits, so no WRITE wrapping is needed in practice.
        val ring = ByteArray(1 shl windowBits)
        val st = State(br, ring)
        st.maxBackwardDistance = ring.size - 16

        val output = ByteBuffer()

        try {
            runStateMachine(st, br, output, ring)
        } catch (e: Exception) {
            // Capture the ring contents so far for the divergence harness.
            partialCapture?.let { pc ->
                for (i in 0 until minOf(st.pos, ring.size)) pc.writeByte(ring[i])
            }
            throw e
        }
        return output.toByteArray()
    }

    private fun runStateMachine(st: State, br: BitReader, output: ByteBuffer, ring: ByteArray) {
        while (st.runningState != ST_FINISHED) {
            when (st.runningState) {
                ST_BLOCK_START -> {
                    if (st.inputEnd) {
                        st.nextRunningState = ST_FINISHED
                        st.bytesToWrite = st.pos
                        st.bytesWritten = 0
                        st.runningState = ST_WRITE
                        continue
                    }
                    // decodeMetaBlockLength
                    st.inputEnd = st.br.readBits(1) == 1
                    st.metaBlockLength = 0
                    st.isUncompressed = false
                    st.isMetadata = false
                    if (st.inputEnd && st.br.readBits(1) != 0) {
                        continue
                    }
                    val sizeNibbles = st.br.readBits(2) + 4
                    if (sizeNibbles == 7) {
                        st.isMetadata = true
                        if (st.br.readBits(1) != 0) throw IllegalArgumentException("Corrupted reserved bit")
                        val sizeBytes = st.br.readBits(2)
                        if (sizeBytes == 0) {
                            st.metaBlockLength = 0
                            continue
                        }
                        for (i in 0 until sizeBytes) {
                            val bits = st.br.readBits(8)
                            if (bits == 0 && i + 1 == sizeBytes && sizeBytes > 1) {
                                throw IllegalArgumentException("Exuberant nibble")
                            }
                            st.metaBlockLength = st.metaBlockLength or (bits shl (i * 8))
                        }
                    } else {
                        for (i in 0 until sizeNibbles) {
                            val bits = st.br.readBits(4)
                            if (bits == 0 && i + 1 == sizeNibbles && sizeNibbles > 4) {
                                throw IllegalArgumentException("Exuberant nibble")
                            }
                            st.metaBlockLength = st.metaBlockLength or (bits shl (i * 4))
                        }
                    }
                    st.metaBlockLength++
                    if (!st.inputEnd) st.isUncompressed = st.br.readBits(1) == 1
                    if (st.metaBlockLength == 0 && !st.isMetadata) continue
                    if (st.isUncompressed || st.isMetadata) {
                        val padding = (64 - st.br.bitOffset) and 7
                        if (padding != 0) {
                            val paddingBits = st.br.readBits(padding)
                            if (paddingBits != 0) throw IllegalArgumentException("Corrupted padding bits")
                        }
                        st.runningState = if (st.isMetadata) ST_READ_METADATA else ST_COPY_UNCOMPRESSED
                    } else {
                        st.runningState = ST_COMPRESSED_BLOCK_START
                    }
                }

                ST_COMPRESSED_BLOCK_START -> {
                    // readMetablockHuffmanCodesAndContextMaps
                    readMetablockHuffmanCodesAndContextMaps(st)
                    st.runningState = ST_MAIN_LOOP
                }

                ST_MAIN_LOOP -> {
                    if (st.metaBlockLength <= 0) {
                        st.runningState = ST_BLOCK_START
                        continue
                    }
                    if (st.blockLength[1] == 0) decodeCommandBlockSwitch(st)
                    st.blockLength[1]--
                    st.br.fillBitWindow()
                    val cmdCode = readSymbol(st.h1codes, st.treeCommandOffset, st.br)
                    var rangeIdx = cmdCode ushr 6
                    st.distanceCode = 0
                    if (rangeIdx >= 2) {
                        rangeIdx -= 2
                        st.distanceCode = -1
                    }
                    val insertCode = INSERT_RANGE_LUT[rangeIdx] + ((cmdCode ushr 3) and 7)
                    val copyCode = COPY_RANGE_LUT[rangeIdx] + (cmdCode and 7)
                    st.insertLength = INSERT_LENGTH_OFFSET[insertCode] +
                        st.br.readBits(INSERT_LENGTH_N_BITS[insertCode])
                    st.copyLength = COPY_LENGTH_OFFSET[copyCode] +
                        st.br.readBits(COPY_LENGTH_N_BITS[copyCode])
                    st.j = 0
                    st.runningState = ST_INSERT_LOOP
                }

                ST_INSERT_LOOP -> {
                    if (!insertLiterals(st)) {
                        // WRITE was triggered (ring overflow). Handle below.
                        continue
                    }
                    st.metaBlockLength -= st.insertLength
                    if (st.metaBlockLength <= 0) {
                        st.runningState = ST_MAIN_LOOP
                        continue
                    }
                    readDistance(st)
                    // readDistance may route ST_TRANSFORM (dictionary ref); preserve it.
                    if (st.runningState != ST_TRANSFORM) st.runningState = ST_COPY_LOOP
                }

                ST_COPY_LOOP -> {
                    copyRun(st)
                    if (st.runningState == ST_COPY_LOOP) st.runningState = ST_MAIN_LOOP
                }

                ST_READ_METADATA -> {
                    while (st.metaBlockLength > 0) {
                        st.br.readBits(8)
                        st.metaBlockLength--
                    }
                    st.runningState = ST_BLOCK_START
                }

                ST_COPY_UNCOMPRESSED -> {
                    var chunk = minOf(st.ring.size - st.pos, st.metaBlockLength)
                    if (st.metaBlockLength <= 0) {
                        st.runningState = ST_BLOCK_START
                        continue
                    }
                    // drain accumulator bytes then read raw
                    chunk = minOf(st.ring.size - st.pos, st.metaBlockLength)
                    for (i in 0 until chunk) {
                        st.ring[st.pos++] = st.br.readBits(8).toByte()
                        st.metaBlockLength--
                    }
                    if (st.pos == st.ring.size) {
                        st.nextRunningState = ST_COPY_UNCOMPRESSED
                        st.bytesToWrite = st.ring.size
                        st.bytesWritten = 0
                        st.runningState = ST_WRITE
                        continue
                    }
                    st.runningState = ST_BLOCK_START
                }

                ST_TRANSFORM -> {
                    // Dictionary transform: copy the dictionary word (possibly transformed)
                    // into the ring buffer at copyDst.
                    applyDictionaryTransform(st)
                    st.runningState = ST_MAIN_LOOP
                }

                ST_COPY_WRAP_BUFFER -> {
                    val n = st.copyDst - st.ring.size
                    if (n > 0) {
                        val src = st.ring
                        val sz = st.ring.size
                        for (i in 0 until n) st.ring[i] = src[sz + i]
                    }
                    st.runningState = ST_MAIN_LOOP
                }

                ST_WRITE -> {
                    // Copy ring[bytesWritten .. bytesToWrite) to output.
                    val n = st.bytesToWrite - st.bytesWritten
                    if (n > 0) {
                        output.write(st.ring, st.bytesWritten, n)
                        partialCapture?.write(st.ring, st.bytesWritten, n)
                    }
                    st.bytesWritten = st.bytesToWrite
                    st.pos = st.pos and st.ringBufferMask
                    st.runningState = st.nextRunningState
                }
            }
        }

        // Final flush: whatever remains in the ring buffer from the last block
        // (the reference does this via WRITE when inputEnd is hit).
        partialCapture?.let { pc ->
            var i = st.bytesWritten
            while (i < st.bytesToWrite) {
                pc.writeByte(st.ring[i]); i++
            }
        }
    }

    // ── Sub-operations ──────────────────────────────────────────────────

    private fun readMetablockHuffmanCodesAndContextMaps(st: State) {
        for (i in 0 until 3) {
            st.br.fillBitWindow()
            st.numBlockTypes[i] = decodeVarLenUnsignedByte(st.br) + 1
            st.blockLength[i] = 1 shl 28
            if (st.numBlockTypes[i] > 1) {
                readHuffmanCode(st.numBlockTypes[i] + 2, st.blockTypeTrees, i * HUFFMAN_MAX_TABLE_SIZE, st.br)
                readHuffmanCode(NUM_BLOCK_LENGTH_CODES, st.blockLenTrees, i * HUFFMAN_MAX_TABLE_SIZE, st.br)
                st.blockLength[i] = readBlockLength(st.blockLenTrees, i * HUFFMAN_MAX_TABLE_SIZE, st.br)
            }
        }
        st.br.fillBitWindow()
        st.distancePostfixBits = st.br.readBits(2)
        st.numDirectDistanceCodes =
            NUM_DISTANCE_SHORT_CODES + (st.br.readBits(4) shl st.distancePostfixBits)
        st.distancePostfixMask = (1 shl st.distancePostfixBits) - 1
        val numDistanceCodes = st.numDirectDistanceCodes + (48 shl st.distancePostfixBits)

        st.contextModes = ByteArray(st.numBlockTypes[0])
        var i = 0
        while (i < st.numBlockTypes[0]) {
            val limit = minOf(i + 96, st.numBlockTypes[0])
            while (i < limit) {
                st.contextModes[i] = (st.br.readBits(2) shl 1).toByte()
                i++
            }
        }

        st.contextMap = ByteArray(st.numBlockTypes[0] shl LITERAL_CONTEXT_BITS)
        val numLiteralTrees = decodeContextMap(
            st.numBlockTypes[0] shl LITERAL_CONTEXT_BITS, st.contextMap, st.br,
        )
        st.trivialLiteralContext = true
        for (jj in 0 until (st.numBlockTypes[0] shl LITERAL_CONTEXT_BITS)) {
            if (st.contextMap[jj].toInt() != (jj shr LITERAL_CONTEXT_BITS)) {
                st.trivialLiteralContext = false
                break
            }
        }

        st.distContextMap = ByteArray(st.numBlockTypes[2] shl DISTANCE_CONTEXT_BITS)
        val numDistTrees = decodeContextMap(
            st.numBlockTypes[2] shl DISTANCE_CONTEXT_BITS, st.distContextMap, st.br,
        )

        st.h0codes = IntArray(numLiteralTrees * HUFFMAN_MAX_TABLE_SIZE)
        st.h0trees = IntArray(numLiteralTrees)
        var next = 0
        for (t in 0 until numLiteralTrees) {
            st.h0trees[t] = next
            readHuffmanCode(NUM_LITERAL_CODES, st.h0codes, next, st.br)
            next += HUFFMAN_MAX_TABLE_SIZE
        }

        st.h1codes = IntArray(st.numBlockTypes[1] * HUFFMAN_MAX_TABLE_SIZE)
        st.h1trees = IntArray(st.numBlockTypes[1])
        next = 0
        for (t in 0 until st.numBlockTypes[1]) {
            st.h1trees[t] = next
            readHuffmanCode(NUM_INSERT_AND_COPY_CODES, st.h1codes, next, st.br)
            next += HUFFMAN_MAX_TABLE_SIZE
        }

        st.h2codes = IntArray(numDistTrees * HUFFMAN_MAX_TABLE_SIZE)
        st.h2trees = IntArray(numDistTrees)
        next = 0
        for (t in 0 until numDistTrees) {
            st.h2trees[t] = next
            readHuffmanCode(numDistanceCodes, st.h2codes, next, st.br)
            next += HUFFMAN_MAX_TABLE_SIZE
        }

        st.contextMapSlice = 0
        st.distContextMapSlice = 0
        val cm = st.contextModes[0].toInt()
        st.contextLookupOffset1 = BrotliLookup.LOOKUP_OFFSETS[cm]
        st.contextLookupOffset2 = BrotliLookup.LOOKUP_OFFSETS[cm + 1]
        st.literalTreeIndex = 0
        st.literalTree = st.h0trees[0]
        st.treeCommandOffset = st.h1trees[0]

        st.blockTypeRb[0] = 1
        st.blockTypeRb[2] = 1
        st.blockTypeRb[4] = 1
        st.blockTypeRb[1] = 0
        st.blockTypeRb[3] = 0
        st.blockTypeRb[5] = 0
    }

    /** Reads the insert-length literals. Returns true if loop completes; false if WRITE triggered. */
    private fun insertLiterals(st: State): Boolean {
        val ringMask = st.ringBufferMask
        if (st.trivialLiteralContext) {
            while (st.j < st.insertLength) {
                st.br.fillBitWindow()
                if (st.blockLength[0] == 0) decodeLiteralBlockSwitch(st)
                st.blockLength[0]--
                st.br.fillBitWindow()
                st.ring[st.pos] = readSymbol(st.h0codes, st.literalTree, st.br).toByte()
                st.j++
                if (st.pos++ == ringMask) {
                    st.nextRunningState = ST_INSERT_LOOP
                    st.bytesToWrite = st.ring.size
                    st.bytesWritten = 0
                    st.runningState = ST_WRITE
                    return false
                }
            }
        } else {
            var prevByte1 = st.ring[(st.pos - 1) and ringMask].toInt() and 0xFF
            var prevByte2 = st.ring[(st.pos - 2) and ringMask].toInt() and 0xFF
            while (st.j < st.insertLength) {
                st.br.fillBitWindow()
                if (st.blockLength[0] == 0) decodeLiteralBlockSwitch(st)
                val treeIdx = st.contextMap[
                    st.contextMapSlice + (
                        BrotliLookup.LOOKUP[st.contextLookupOffset1 + prevByte1] or
                            BrotliLookup.LOOKUP[st.contextLookupOffset2 + prevByte2]
                    )
                ].toInt() and 0xFF
                st.blockLength[0]--
                prevByte2 = prevByte1
                st.br.fillBitWindow()
                prevByte1 = readSymbol(st.h0codes, st.h0trees[treeIdx], st.br)
                st.ring[st.pos] = prevByte1.toByte()
                st.j++
                if (st.pos++ == ringMask) {
                    st.nextRunningState = ST_INSERT_LOOP
                    st.bytesToWrite = st.ring.size
                    st.bytesWritten = 0
                    st.runningState = ST_WRITE
                    return false
                }
            }
        }
        return true
    }

    private fun readDistance(st: State) {
        if (st.distanceCode < 0) {
            st.br.fillBitWindow()
            if (st.blockLength[2] == 0) decodeDistanceBlockSwitch(st)
            st.blockLength[2]--
            st.br.fillBitWindow()
            val treeIdx = st.distContextMap[
                st.distContextMapSlice + (if (st.copyLength > 4) 3 else st.copyLength - 2)
            ].toInt() and 0xFF
            st.distanceCode = readSymbol(st.h2codes, st.h2trees[treeIdx], st.br)
            if (st.distanceCode >= st.numDirectDistanceCodes) {
                st.distanceCode -= st.numDirectDistanceCodes
                val postfix = st.distanceCode and st.distancePostfixMask
                st.distanceCode = st.distanceCode ushr st.distancePostfixBits
                val n = (st.distanceCode ushr 1) + 1
                val offset = ((2 + (st.distanceCode and 1)) shl n) - 4
                st.distanceCode = st.numDirectDistanceCodes + postfix +
                    ((offset + st.br.readBits(n)) shl st.distancePostfixBits)
            }
        }

        st.distance = translateShortCodes(st.distanceCode, st.distRb, st.distRbIdx)
        if (st.distance < 0) throw IllegalArgumentException("Negative distance")

        if (st.maxDistance != st.maxBackwardDistance && st.pos < st.maxBackwardDistance) {
            st.maxDistance = st.pos
        } else {
            st.maxDistance = st.maxBackwardDistance
        }
        val maxDistance = st.maxDistance
        st.copyDst = st.pos

        if (st.distance > maxDistance) {
            // Dictionary reference: the copy is served from Brotli's static dictionary
            // (possibly with a word transform). The reference does NOT check
            // copyLength > metaBlockLength here (the transformed word's length may differ);
            // out-of-range copyLength is caught inside applyDictionaryTransform.
            if (DEBUG_DICT_TRACE && traceCount < 10) {
                traceCount++
                System.err.println(
                    "[route] distance=${st.distance} > maxDist=$maxDistance pos=${st.pos} " +
                        "copyLen=${st.copyLength} meta=${st.metaBlockLength} wordId=${st.distance - maxDistance - 1}",
                )
            }
            st.runningState = ST_TRANSFORM
            st.nextRunningState = ST_MAIN_LOOP
            st.j = 0
            return
        }
        if (st.distanceCode > 0) {
            st.distRb[st.distRbIdx and 3] = st.distance
            st.distRbIdx++
        }
        if (st.copyLength > st.metaBlockLength) {
            throw IllegalArgumentException(
                "Invalid backward reference: copyLen=${st.copyLength} metaLen=${st.metaBlockLength} " +
                    "dist=${st.distance} maxDist=$maxDistance pos=${st.pos}",
            )
        }
        st.j = 0
    }

    private fun copyRun(st: State) {
        val ringMask = st.ringBufferMask
        val srcStart = (st.pos - st.distance) and ringMask
        val dstStart = st.pos
        val rem = st.copyLength - st.j
        if ((srcStart + rem < ringMask) && (dstStart + rem < ringMask)) {
            var src = srcStart
            var dst = dstStart
            for (k in 0 until rem) {
                st.ring[dst++] = st.ring[src++]
            }
            st.j += rem
            st.metaBlockLength -= rem
            st.pos += rem
        } else {
            while (st.j < st.copyLength) {
                st.ring[st.pos] = st.ring[(st.pos - st.distance) and ringMask]
                st.metaBlockLength--
                st.j++
                if (st.pos++ == ringMask) {
                    st.nextRunningState = ST_COPY_LOOP
                    st.bytesToWrite = st.ring.size
                    st.bytesWritten = 0
                    st.runningState = ST_WRITE
                    return
                }
            }
        }
    }

    private fun decodeLiteralBlockSwitch(st: State) {
        st.br.fillBitWindow()
        var blockType = readSymbol(st.blockTypeTrees, 0, st.br)
        st.blockLength[0] = readBlockLength(st.blockLenTrees, 0, st.br)
        blockType = when (blockType) {
            1 -> st.blockTypeRb[1] + 1
            0 -> st.blockTypeRb[0]
            else -> blockType - 2
        }
        if (blockType >= st.numBlockTypes[0]) blockType -= st.numBlockTypes[0]
        st.blockTypeRb[0] = st.blockTypeRb[1]
        st.blockTypeRb[1] = blockType
        st.contextMapSlice = st.blockTypeRb[1] shl LITERAL_CONTEXT_BITS
        st.literalTreeIndex = st.contextMap[st.contextMapSlice].toInt() and 0xFF
        st.literalTree = st.h0trees[st.literalTreeIndex]
        val contextMode = st.contextModes[st.blockTypeRb[1]].toInt()
        st.contextLookupOffset1 = BrotliLookup.LOOKUP_OFFSETS[contextMode]
        st.contextLookupOffset2 = BrotliLookup.LOOKUP_OFFSETS[contextMode + 1]
    }

    private fun decodeCommandBlockSwitch(st: State) {
        st.br.fillBitWindow()
        var blockType = readSymbol(st.blockTypeTrees, 1 * HUFFMAN_MAX_TABLE_SIZE, st.br)
        st.blockLength[1] = readBlockLength(st.blockLenTrees, 1 * HUFFMAN_MAX_TABLE_SIZE, st.br)
        blockType = when (blockType) {
            1 -> st.blockTypeRb[3] + 1
            0 -> st.blockTypeRb[2]
            else -> blockType - 2
        }
        if (blockType >= st.numBlockTypes[1]) blockType -= st.numBlockTypes[1]
        st.blockTypeRb[2] = st.blockTypeRb[3]
        st.blockTypeRb[3] = blockType
        st.treeCommandOffset = st.h1trees[st.blockTypeRb[3]]
    }

    private fun decodeDistanceBlockSwitch(st: State) {
        st.br.fillBitWindow()
        var blockType = readSymbol(st.blockTypeTrees, 2 * HUFFMAN_MAX_TABLE_SIZE, st.br)
        st.blockLength[2] = readBlockLength(st.blockLenTrees, 2 * HUFFMAN_MAX_TABLE_SIZE, st.br)
        blockType = when (blockType) {
            1 -> st.blockTypeRb[5] + 1
            0 -> st.blockTypeRb[4]
            else -> blockType - 2
        }
        if (blockType >= st.numBlockTypes[2]) blockType -= st.numBlockTypes[2]
        st.blockTypeRb[4] = st.blockTypeRb[5]
        st.blockTypeRb[5] = blockType
        st.distContextMapSlice = st.blockTypeRb[5] shl DISTANCE_CONTEXT_BITS
    }

    // ── Static dictionary ────────────────────────────────────────────────

    private fun getOmitFirst(type: Int): Int =
        if (type >= OMIT_FIRST_1) type - OMIT_FIRST_1 + 1 else 0

    private fun getOmitLast(type: Int): Int =
        if (type <= OMIT_LAST_9) type - OMIT_LAST_1 + 1 else 0

    // Dictionary index tables (mirror Dictionary.java).
    private val OFFSETS_BY_LENGTH = intArrayOf(
        0, 0, 0, 0, 0, 4096, 9216, 21504, 35840, 44032, 53248, 63488, 74752, 87040, 93696,
        100864, 104704, 106752, 108928, 113536, 115968, 118528, 119872, 121280, 122016,
    )
    private val SIZE_BITS_BY_LENGTH = intArrayOf(
        0, 0, 0, 0, 10, 10, 11, 11, 10, 10, 10, 10, 10, 9, 9, 8, 7, 7, 8, 7, 7, 6, 6, 5, 5,
    )

    private const val MIN_WORD_LENGTH = 4
    private const val MAX_WORD_LENGTH = 24

    /** (prefix, transformType, suffix) triples, mirroring Transform.TRANSFORMS. */
    private val TRANSFORMS = arrayOf(
        Triple("", IDENTITY, ""), Triple("", IDENTITY, " "), Triple(" ", IDENTITY, " "),
        Triple("", OMIT_FIRST_1, ""), Triple("", UPPERCASE_FIRST, " "), Triple("", IDENTITY, " the "),
        Triple(" ", IDENTITY, ""), Triple("s ", IDENTITY, " "), Triple("", IDENTITY, " of "),
        Triple("", UPPERCASE_FIRST, ""), Triple("", IDENTITY, " and "), Triple("", OMIT_FIRST_2, ""),
        Triple("", OMIT_LAST_1, ""), Triple(", ", IDENTITY, " "), Triple("", IDENTITY, ", "),
        Triple(" ", UPPERCASE_FIRST, " "), Triple("", IDENTITY, " in "), Triple("", IDENTITY, " to "),
        Triple("e ", IDENTITY, " "), Triple("", IDENTITY, "\""), Triple("", IDENTITY, "."),
        Triple("", IDENTITY, "\">"), Triple("", IDENTITY, "\n"), Triple("", OMIT_LAST_3, ""),
        Triple("", IDENTITY, "]"), Triple("", IDENTITY, " for "), Triple("", OMIT_FIRST_3, ""),
        Triple("", OMIT_LAST_2, ""), Triple("", IDENTITY, " a "), Triple("", IDENTITY, " that "),
        Triple(" ", UPPERCASE_FIRST, ""), Triple("", IDENTITY, ". "), Triple(".", IDENTITY, ""),
        Triple(" ", IDENTITY, ", "), Triple("", OMIT_FIRST_4, ""), Triple("", IDENTITY, " with "),
        Triple("", IDENTITY, "'"), Triple("", IDENTITY, " from "), Triple("", IDENTITY, " by "),
        Triple("", OMIT_FIRST_5, ""), Triple("", OMIT_FIRST_6, ""), Triple(" the ", IDENTITY, ""),
        Triple("", OMIT_LAST_4, ""), Triple("", IDENTITY, ". The "), Triple("", UPPERCASE_ALL, ""),
        Triple("", IDENTITY, " on "), Triple("", IDENTITY, " as "), Triple("", IDENTITY, " is "),
        Triple("", OMIT_LAST_7, ""), Triple("", OMIT_LAST_1, "ing "), Triple("", IDENTITY, "\n\t"),
        Triple("", IDENTITY, ":"), Triple(" ", IDENTITY, ". "), Triple("", IDENTITY, "ed "),
        Triple("", OMIT_FIRST_9, ""), Triple("", OMIT_FIRST_7, ""), Triple("", OMIT_LAST_6, ""),
        Triple("", IDENTITY, "("), Triple("", UPPERCASE_FIRST, ", "), Triple("", OMIT_LAST_8, ""),
        Triple("", IDENTITY, " at "), Triple("", IDENTITY, "ly "), Triple(" the ", IDENTITY, " of "),
        Triple("", OMIT_LAST_5, ""), Triple("", OMIT_LAST_9, ""), Triple(" ", UPPERCASE_FIRST, ", "),
        Triple("", UPPERCASE_FIRST, "\""), Triple(".", IDENTITY, "("), Triple("", UPPERCASE_ALL, " "),
        Triple("", UPPERCASE_FIRST, "\">"), Triple("", IDENTITY, "=\""), Triple(" ", IDENTITY, "."),
        Triple(".com/", IDENTITY, ""), Triple(" the ", IDENTITY, " of the "), Triple("", UPPERCASE_FIRST, "'"),
        Triple("", IDENTITY, ". This "), Triple("", IDENTITY, ","), Triple(".", IDENTITY, " "),
        Triple("", UPPERCASE_FIRST, "("), Triple("", UPPERCASE_FIRST, "."), Triple("", IDENTITY, " not "),
        Triple(" ", IDENTITY, "=\""), Triple("", IDENTITY, "er "), Triple(" ", UPPERCASE_ALL, " "),
        Triple("", IDENTITY, "al "), Triple(" ", UPPERCASE_ALL, ""), Triple("", IDENTITY, "='"),
        Triple("", UPPERCASE_ALL, "\""), Triple("", UPPERCASE_FIRST, ". "), Triple(" ", IDENTITY, "("),
        Triple("", IDENTITY, "ful "), Triple(" ", UPPERCASE_FIRST, ". "), Triple("", IDENTITY, "ive "),
        Triple("", IDENTITY, "less "), Triple("", UPPERCASE_ALL, "'"), Triple("", IDENTITY, "est "),
        Triple(" ", UPPERCASE_FIRST, "."), Triple("", UPPERCASE_ALL, "\">"), Triple(" ", IDENTITY, "='"),
        Triple("", UPPERCASE_FIRST, ","), Triple("", IDENTITY, "ize "), Triple("", UPPERCASE_ALL, "."),
        Triple("Â ", IDENTITY, ""), Triple(" ", IDENTITY, ","), Triple("", UPPERCASE_FIRST, "=\""),
        Triple("", UPPERCASE_ALL, "=\""), Triple("", IDENTITY, "ous "), Triple("", UPPERCASE_ALL, ", "),
        Triple("", UPPERCASE_FIRST, "='"), Triple(" ", UPPERCASE_FIRST, ","), Triple(" ", UPPERCASE_ALL, "=\""),
        Triple(" ", UPPERCASE_ALL, ", "), Triple("", UPPERCASE_ALL, ","), Triple("", UPPERCASE_ALL, "("),
        Triple("", UPPERCASE_ALL, ". "), Triple(" ", UPPERCASE_ALL, "."), Triple("", UPPERCASE_ALL, "='"),
        Triple(" ", UPPERCASE_ALL, ". "), Triple(" ", UPPERCASE_FIRST, "=\""), Triple(" ", UPPERCASE_ALL, "='"),
        Triple(" ", UPPERCASE_FIRST, "='"),
    )

    // The decompressed dictionary, cached once.
    private var dictionaryCache: ByteArray? = null

    // Diagnostic trace (JVM harness only; off for the shipped artifact).
    private const val DEBUG_DICT_TRACE = false
    private var traceCount = 0
    /** Captures partially-decompressed output for divergence detection (JVM harness). */
    internal var partialCapture: ByteBuffer? = null

    /** Decompress the embedded Brotli static dictionary (lazily, once). */
    private fun dictionaryBytes(): ByteArray {
        dictionaryCache?.let { return it }
        val b64 = BrotliDictionary.COMPRESSED_BASE64
        val raw = decodeBase64(b64)
        val dict = decompress(raw)
        dictionaryCache = dict
        return dict
    }

    private fun decodeBase64(s: String): ByteArray {
        // Bare-bones base64 decoder (works on JVM + JS, no java.util.Base64).
        val table = IntArray(128) { -1 }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (i in 0 until chars.length) table[chars[i].code] = i
        val out = ByteBuffer()
        var acc = 0
        var bits = 0
        for (i in 0 until s.length) {
            val c = s[i]
            if (c == '=') break
            val v = if (c.code < 128) table[c.code] else -1
            if (v < 0) continue
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.writeByte(((acc ushr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    /** Apply a dictionary word (with transform) into the ring buffer at copyDst. */
    private fun applyDictionaryTransform(st: State) {
        val copyLen = st.copyLength
        if (DEBUG_DICT_TRACE && traceCount < 40) {
            traceCount++
            System.err.println(
                "[dct-entry] copyLen=$copyLen dist=${st.distance} maxDist=${st.maxDistance} " +
                    "pos=${st.pos} wordId=${st.distance - st.maxDistance - 1}",
            )
        }
        if (copyLen < MIN_WORD_LENGTH || copyLen > MAX_WORD_LENGTH) {
            throw IllegalArgumentException(
                "dict: copyLen=$copyLen out of [4,24], distance=${st.distance}, " +
                    "maxBackward=${st.maxBackwardDistance}",
            )
        }
        val dict = dictionaryBytes()
        val offset = OFFSETS_BY_LENGTH[copyLen]
        val wordId = st.distance - st.maxDistance - 1
        val shift = SIZE_BITS_BY_LENGTH[copyLen]
        val mask = (1 shl shift) - 1
        val wordIdx = wordId and mask
        val transformIdx = wordId ushr shift
        if (transformIdx >= TRANSFORMS.size) {
            throw IllegalArgumentException(
                "dict: transformIdx=$transformIdx >= ${TRANSFORMS.size}, copyLen=$copyLen, " +
                    "distance=${st.distance}, maxBackward=${st.maxBackwardDistance}, wordId=$wordId",
            )
        }
        val (prefix, type, suffix) = TRANSFORMS[transformIdx]
        var outPos = st.copyDst
        val ring = st.ring
        val ringMask = st.ringBufferMask
        if (DEBUG_DICT_TRACE && traceCount < 10) {
            traceCount++
            System.err.println(
                "[dct] copyLen=$copyLen wordId=$wordId wordIdx=$wordIdx transform=$transformIdx " +
                    "type=$type pos=${st.pos} meta=${st.metaBlockLength}",
            )
        }

        fun putAt(idx: Int, value: Byte) {
            ring[idx and ringMask] = value
        }

        // Copy prefix.
        val prefixBytes = prefix.encodeToByteArray()
        for (b in prefixBytes) {
            putAt(outPos, b); outPos++
        }

        // Copy trimmed word.
        var wordOff = offset + wordIdx * copyLen
        var len = copyLen
        val omitFirst = getOmitFirst(type)
        wordOff += minOf(omitFirst, len)
        len -= minOf(omitFirst, len)
        len -= getOmitLast(type)
        val word = dict
        var i = 0
        while (i < len) {
            putAt(outPos, word[wordOff]); wordOff++; outPos++; i++
        }

        if (type == UPPERCASE_ALL || type == UPPERCASE_FIRST) {
            var uppercaseOffset = outPos - len
            var upLen = if (type == UPPERCASE_FIRST) 1 else len
            while (upLen > 0) {
                val byteAt = ring[uppercaseOffset and ringMask].toInt() and 0xFF
                if (byteAt < 0xc0) {
                    if (byteAt in 0x61..0x7a) putAt(uppercaseOffset, (byteAt xor 32).toByte())
                    uppercaseOffset++
                    upLen--
                } else if (byteAt < 0xe0) {
                    putAt(uppercaseOffset + 1, (ring[(uppercaseOffset + 1) and ringMask].toInt() xor 32).toByte())
                    uppercaseOffset += 2
                    upLen -= 2
                } else {
                    putAt(uppercaseOffset + 2, (ring[(uppercaseOffset + 2) and ringMask].toInt() xor 5).toByte())
                    uppercaseOffset += 3
                    upLen -= 3
                }
            }
        }

        // Copy suffix.
        val suffixBytes = suffix.encodeToByteArray()
        for (b in suffixBytes) {
            putAt(outPos, b); outPos++
        }

        val totalLen = outPos - st.copyDst
        st.pos = st.copyDst + totalLen
        st.metaBlockLength -= totalLen
    }
}