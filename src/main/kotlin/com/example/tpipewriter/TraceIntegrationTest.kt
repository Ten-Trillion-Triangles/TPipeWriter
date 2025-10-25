package com.example.tpipewriter

import com.TTT.Pipeline.Pipeline
import com.TTT.Pipeline.Connector
import com.TTT.Debug.TraceStreamMerger
import com.TTT.Debug.PipeTracer
import com.TTT.Debug.TraceConfig
import com.TTT.Debug.TraceFormat

fun main() {
    // Enable tracing
    PipeTracer.enable()
    
    // Create main pipeline
    val mainPipeline = Pipeline()
        .enableTracing(TraceConfig())
    
    // Create dialogue connector
    val dialogueConnector = Connector()
        .enableTracing(TraceConfig())
    
    // Simulate some execution to generate traces
    println("Main pipeline trace ID: ${mainPipeline.getTraceId()}")
    println("Dialogue connector trace ID: ${dialogueConnector.getTraceId()}")
    
    // Get traces before merge
    val mainTracesBefore = PipeTracer.getTrace(mainPipeline.getTraceId() ?: "")
    val dialogueTracesBefore = PipeTracer.getTrace(dialogueConnector.getTraceId() ?: "")
    
    println("Main pipeline events before merge: ${mainTracesBefore.size}")
    println("Dialogue connector events before merge: ${dialogueTracesBefore.size}")
    
    // Try bubble merge
    TraceStreamMerger.bubbleMerge(mainPipeline, dialogueConnector)
    
    // Get traces after merge
    val mainTracesAfter = PipeTracer.getTrace(mainPipeline.getTraceId() ?: "")
    val dialogueTracesAfter = PipeTracer.getTrace(dialogueConnector.getTraceId() ?: "")
    
    println("Main pipeline events after merge: ${mainTracesAfter.size}")
    println("Dialogue connector events after merge: ${dialogueTracesAfter.size}")
    
    // Export traces to see what happens
    val mainTraceHtml = PipeTracer.exportTrace(mainPipeline.getTraceId() ?: "", TraceFormat.HTML)
    val dialogueTraceHtml = PipeTracer.exportTrace(dialogueConnector.getTraceId() ?: "", TraceFormat.HTML)
    
    println("Main trace HTML length: ${mainTraceHtml.length}")
    println("Dialogue trace HTML length: ${dialogueTraceHtml.length}")
}
