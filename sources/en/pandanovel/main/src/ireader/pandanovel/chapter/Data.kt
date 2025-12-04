package ireader.pandanovel.chapter

import kotlinx.serialization.Serializable

@Serializable
data class Data(
    val selectList: List<String>,
    val pages: Int,
    val list: List<Info>,
    val selectNumList: List<Int>
)
