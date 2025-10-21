package Structs

@kotlinx.serialization.Serializable
data class RequestList(val elems : List<String> = mutableListOf<String>())
