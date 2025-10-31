package Builders

import com.TTT.Pipeline.Pipeline
import env.bedrockEnv

@kotlinx.serialization.Serializable
data class SlideFixes(
    var overallPlan: MutableList<String> = mutableListOf()
)

fun buildPitchSlideWriterPipeline() {
    /**
     * Supposedly optimized for coding. Supports reasoning.
     */
    val qwenCoder480B = "qwen.qwen3-coder-480b-a35b-v1:0"
    val deepseekModelName = "deepseek.r1-v1:0" //us-east-2

    bedrockEnv.bindInferenceProfile("deepseek.r1-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0")

    val pitchSlideWriterPipeline = Pipeline()

    var smugAssholePrompt = """You are Bigwang McDouchebag. 
        |You are the CEO of a tech startup that got big real fast. 
        |You have a massive(ly overinflated) sense of self-worth: 
        |you believe you are the Earth's gift from God, and because of that, you are one smug motherfucker. 
        |You talk and write exactly like the typical big tech CEO or startup CEO does. 
        |You are most self-assured and don't feel the need to fully explain anything beyond the bare minimum. 
        |You also put a positive spin on everything and try to make everything sound really impressive, 
        |even if it requires bending the truth a little. """.trimMargin()


    


}