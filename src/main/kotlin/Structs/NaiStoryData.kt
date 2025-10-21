package Structs

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class NaiStoryData(
    val storyContainerVersion: Int,
    val metadata: StoryMetadata,
    val content: StoryContent
)

@Serializable
data class StoryMetadata(
    val storyMetadataVersion: Int = 0,
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val textPreview: String = "",
    val isTA: Boolean = false,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val lastUpdatedAt: Long = 0L,
    val isModified: Boolean = false,
    val hasDocument: Boolean = false
)

@Serializable
data class StoryContent(
    val storyContentVersion: Int = 0,
    val settings: StorySettings = StorySettings(),
    val document: String = "",
    val context: List<ContextEntry> = emptyList(),
    val lorebook: NaiLorebook = NaiLorebook(),
    val storyContextConfig: NaiContextConfig = NaiContextConfig(),
    val ephemeralContext: List<String> = emptyList(),
    val contextDefaults: ContextDefaults = ContextDefaults(),
    val settingsDirty: Boolean = false,
    val didGenerate: Boolean = false,
    val phraseBiasGroups: List<BiasGroup> = emptyList(),
    val bannedSequenceGroups: List<BannedSequenceGroup> = emptyList()
)

@Serializable
data class StorySettings(
    val parameters: TextGenerationParameters = TextGenerationParameters(),
    val preset: String = "",
    val trimResponses: Boolean = false,
    val banBrackets: Boolean = false,
    val prefix: String = "",
    val dynamicPenaltyRange: Boolean = false,
    val prefixMode: Int = 0,
    val mode: Int = 0,
    val model: String = ""
)

@Serializable
data class TextGenerationParameters(
    val textGenerationSettingsVersion: Int = 0,
    val temperature: Double = 1.0,
    val max_length: Int = 100,
    val min_length: Int = 1,
    val top_k: Int = 0,
    val top_p: Double = 1.0,
    val top_a: Double = 1.0,
    val typical_p: Int = 1,
    val tail_free_sampling: Double = 1.0,
    val repetition_penalty: Double = 1.0,
    val repetition_penalty_range: Int = 0,
    val repetition_penalty_slope: Double = 0.0,
    val repetition_penalty_frequency: Double = 0.0,
    val repetition_penalty_presence: Int = 0,
    val repetition_penalty_default_whitelist: Boolean = true,
    val cfg_scale: Int = 1,
    val cfg_uc: String = "",
    val phrase_rep_pen: String = "",
    val top_g: Int = 0,
    val mirostat_tau: Int = 0,
    val mirostat_lr: Int = 0,
    val math1_temp: Int = 0,
    val math1_quad: Int = 0,
    val math1_quad_entropy_scale: Int = 0,
    val min_p: Int = 0,
    val order: List<OrderItem> = emptyList()
)

@Serializable
data class OrderItem(
    val enabled: Boolean,
    val id: String
)

@Serializable
data class ContextEntry(
    @SerialName("contextConfig") val contextConfig: NaiContextConfig,
    val text: String
)

@Serializable
@SerialName("contextConfig")
data class NaiContextConfig(
    val suffix: String = "",
    val insertionType: String = "",
    val maximumTrimType: String = "",
    val tokenBudget: Int = 0,
    val trimDirection: String = "",
    val prefix: String = "",
    val reservedTokens: Int = 0,
    val budgetPriority: Int = 0,
    val insertionPosition: Int = 0,
    val allowInsertionInside: Boolean? = null
)

@Serializable
@SerialName("Lorebook")
data class NaiLorebook(
    val lorebookVersion: Int = 0,
    val entries: List<NaiLorebookEntry> = emptyList(),
    val settings: LorebookSettings = LorebookSettings(),
    val categories: List<NaiCategory> = emptyList()
)

@Serializable
data class NaiCategory(
    val name: String
)

@Serializable
@SerialName("LorebookEntry")
data class NaiLorebookEntry(
    val nonStoryActivatable: Boolean,
    val keys: List<String>,
    val searchRange: Int,
    val contextConfig: NaiContextConfig,
    val id: String,
    val category: String,
    val keyRelative: Boolean,
    val enabled: Boolean,
    val lastUpdatedAt: Long,
    val loreBiasGroups: List<BiasGroup>,
    val text: String,
    val displayName: String,
    val forceActivation: Boolean
)

@Serializable
data class LorebookSettings(
    val orderByKeyLocations: Boolean = false
)

@Serializable
data class ContextDefaults(
    val ephemeralDefaults: List<EphemeralDefault> = emptyList(),
    val loreDefaults: List<LoreDefault> = emptyList()
)

@Serializable
data class EphemeralDefault(
    val text: String,
    val contextConfig: NaiContextConfig,
    val startingStep: Int,
    val delay: Int,
    val duration: Int,
    val repeat: Boolean,
    val reverse: Boolean
)

@Serializable
data class LoreDefault(
    val text: String,
    val contextConfig: NaiContextConfig,
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
    val loreBiasGroups: List<BiasGroup>
)

@Serializable
data class BiasGroup(
    val enabled: Boolean,
    val bias: Int,
    val phrases: List<String>,
    val ensureSequenceFinish: Boolean,
    val whenInactive: Boolean,
    val generateOnce: Boolean
)

@Serializable
data class BannedSequenceGroup(
    val sequences: List<String>,
    val enabled: Boolean
)
