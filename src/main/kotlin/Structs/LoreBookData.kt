package Structs

@kotlinx.serialization.Serializable
data class ContextConfig(
    val prefix: String = "",
    val suffix: String = "\n",
    val tokenBudget: Int = 1,
    val reservedTokens: Int = 0,
    val budgetPriority: Int = 400,
    val trimDirection: String = "trimBottom",
    val insertionType: String = "newline",
    val maximumTrimType: String = "sentence",
    val insertionPosition: Int = -1
)

@kotlinx.serialization.Serializable
data class LoreBiasGroup(
    val phrases: List<String> = emptyList(),
    val ensureSequenceFinish: Boolean = false,
    val generateOnce: Boolean = true,
    val bias: Double = 0.0,
    val enabled: Boolean = true,
    val whenInactive: Boolean = false
)

@kotlinx.serialization.Serializable
data class LoreBookEntry(
    val text: String,
    val contextConfig: ContextConfig,
    val lastUpdatedAt: Long,
    val displayName: String,
    val id: String,
    val keys: List<String>,
    val searchRange: Int,
    val enabled: Boolean,
    val forceActivation: Boolean,
    val keyRelative: Boolean,
    val nonStoryActivatable: Boolean,
    val category: String,
    val loreBiasGroups: List<LoreBiasGroup>,
    val hidden: Boolean = false
)

@kotlinx.serialization.Serializable
data class Settings(
    val orderByKeyLocations: Boolean = false
)

@kotlinx.serialization.Serializable
data class CategoryDefaults(
    val text: String = "",
    val contextConfig: ContextConfig,
    val lastUpdatedAt: Long,
    val displayName: String = "New Lorebook Entry",
    val id: String,
    val keys: List<String> = emptyList(),
    val searchRange: Int = 2500,
    val enabled: Boolean = true,
    val forceActivation: Boolean = true,
    val keyRelative: Boolean = false,
    val nonStoryActivatable: Boolean = true,
    val category: String = "",
    val loreBiasGroups: List<LoreBiasGroup>
)

@kotlinx.serialization.Serializable
data class SubcontextSettings(
    val text: String = "",
    val contextConfig: ContextConfig,
    val lastUpdatedAt: Long,
    val displayName: String = "New Lorebook Entry",
    val id: String,
    val keys: List<String> = emptyList(),
    val searchRange: Int = 1000,
    val enabled: Boolean = true,
    val forceActivation: Boolean = false,
    val keyRelative: Boolean = false,
    val nonStoryActivatable: Boolean = false,
    val category: String = "",
    val loreBiasGroups: List<LoreBiasGroup>
)

@kotlinx.serialization.Serializable
data class Category(
    val name: String,
    val id: String,
    val enabled: Boolean = true,
    val createSubcontext: Boolean = false,
    val subcontextSettings: SubcontextSettings,
    val useCategoryDefaults: Boolean = true,
    val categoryDefaults: CategoryDefaults,
    val categoryBiasGroups: List<LoreBiasGroup>,
    val open: Boolean = true
)

@kotlinx.serialization.Serializable
data class LoreBookData(
    val lorebookVersion: Int,
    val entries: List<LoreBookEntry>,
    val settings: Settings,
    val categories: List<Category>
)