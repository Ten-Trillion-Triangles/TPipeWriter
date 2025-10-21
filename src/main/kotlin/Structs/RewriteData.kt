package Structs

import kotlinx.serialization.Serializable

@Serializable
data class ChapterRewriteRequest(
    val originalChapter: String,
    val userRequest: String,
    val chapterIndex: Int
)

@Serializable
data class RewriteAnalysis(
    val identifiedIssues: List<String>,
    val rewriteStrategy: String,
    val loreReferences: List<String>
)

@Serializable
data class ChapterRewriteResult(
    val rewrittenChapter: String,
    val changesApplied: List<String>,
    val loreIssuesFixed: List<String>,
    val styleImprovements: List<String>
)