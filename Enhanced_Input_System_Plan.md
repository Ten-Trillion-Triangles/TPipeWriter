# LLM Implementation Guide: Enhanced Input System

## CRITICAL: Implementation Requirements

**ABSOLUTE MINIMAL CODE APPROACH**: Implement only essential functions. No verbose implementations. Focus on core functionality that directly solves the problem.

## Current State Analysis

### Existing Functions (DO NOT MODIFY THESE YET)
- `safeReadInput()` in Shell.kt (lines 89-156) - Multi-line JSON detection
- `readLineWithClipboard()` in Util.kt - Clipboard macros (@clip, @context, @lore, @file)
- Standard `readln()` calls throughout codebase

### Problem Statement
Need ONE unified function that combines both multi-line detection AND clipboard macro processing.

## IMPLEMENTATION STEPS (Execute in Order)

### STEP 1: Create Core Function
**File**: `/src/main/kotlin/Util/EnhancedInput.kt`

**REQUIRED CODE** (Copy exactly):
```kotlin
package Util

import com.TTT.Context.ContextBank
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

fun readEnhancedInput(): String {
    val firstLine = readln()
    val processedLine = processClipboardMacros(firstLine)
    
    return if (detectMultilineContent(processedLine)) {
        collectMultilineInput(processedLine)
    } else {
        processedLine
    }
}

fun detectMultilineContent(line: String): Boolean {
    val hasOpenBracket = line.count { it == '{' } > line.count { it == '}' } ||
                        line.count { it == '[' } > line.count { it == ']' }
    val endsIncomplete = line.trimEnd().let { 
        it.endsWith(",") || it.endsWith(":") || it.endsWith("\\") 
    }
    val hasCodeBlock = line.contains("```") && line.count { it == '`' } % 6 != 0
    val isLargePaste = line.length > 1000
    
    return hasOpenBracket || endsIncomplete || hasCodeBlock || isLargePaste
}

fun collectMultilineInput(firstLine: String): String {
    val lines = mutableListOf(firstLine)
    println("Multiline input detected. Continue pasting or type 'END', 'c', or 'n' to finish:")
    
    while (true) {
        val line = readln()
        val trimmed = line.trim()
        if (trimmed.uppercase() == "END" || trimmed == "c" || trimmed == "n") break
        lines.add(line)
        
        val combined = lines.joinToString("\n")
        val openBraces = combined.count { it == '{' }
        val closeBraces = combined.count { it == '}' }
        val openBrackets = combined.count { it == '[' }
        val closeBrackets = combined.count { it == ']' }
        
        if (openBraces == closeBraces && openBrackets == closeBrackets && 
            !combined.trimEnd().endsWith(",") && !combined.trimEnd().endsWith(":")) {
            println("Complete structure detected. Input finished.")
            break
        }
    }
    
    return lines.joinToString("\n")
}

fun processClipboardMacros(text: String): String {
    var result = text
    
    if (result.contains("@context")) {
        val context = ContextBank.getContextFromBank("main").toString()
        result = result.replace("@context", context)
    }
    
    if (result.contains("@lore")) {
        val lore = ContextBank.getContextFromBank("main").loreBookKeys.toString()
        result = result.replace("@lore", lore)
    }
    
    if (result.contains("@clip")) {
        val clipContent = try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
        } catch (e: Exception) { "" }
        result = result.replace("@clip", clipContent)
    }
    
    if (result.contains("@file")) {
        val fileIndex = result.indexOf("@file")
        if (fileIndex != -1) {
            val afterFile = result.substring(fileIndex + 5).trim()
            val filePath = afterFile.split(" ")[0]
            val fileContent = try {
                java.io.File(filePath).readText()
            } catch (e: Exception) { "" }
            result = result.replace("@file $filePath", fileContent)
        }
    }
    
    return result
}
```

### STEP 2: Update Shell.kt
**CRITICAL**: Replace ONLY these specific function calls:

1. **Line 89** - Change `readLineWithClipboard()` to `readEnhancedInput()`
2. **Line 156** - Remove entire `safeReadInput()` function call and replace with `readEnhancedInput()`
3. **All other `readLineWithClipboard()` calls** - Replace with `readEnhancedInput()`

**Import Statement**: Add `import Util.readEnhancedInput` at top of Shell.kt

### STEP 3: Update WriterSubshell.kt
**Replace ALL instances of**:
- `readLineWithClipboard()` → `readEnhancedInput()`

**Import Statement**: Add `import Util.readEnhancedInput`

### STEP 4: Update SettingsSubshell.kt
**Replace ALL instances of**:
- `readLineWithClipboard()` → `readEnhancedInput()`

**Import Statement**: Add `import Util.readEnhancedInput`

### STEP 5: Mark Old Functions as Deprecated
**In Util.kt**, add deprecation annotations:
```kotlin
@Deprecated("Use readEnhancedInput() instead")
fun readLineWithClipboard(): String { ... }
```

**In Shell.kt**, add deprecation annotation:
```kotlin
@Deprecated("Use readEnhancedInput() instead")
fun safeReadInput(): String { ... }
```

## EXACT FILES TO MODIFY

### Files to CREATE:
1. `/src/main/kotlin/Util/EnhancedInput.kt` - Contains the complete implementation above

### Files to MODIFY:
1. `/src/main/kotlin/Shell/Shell.kt` - Replace function calls, add import
2. `/src/main/kotlin/Shell/WriterSubshell.kt` - Replace function calls, add import  
3. `/src/main/kotlin/Shell/SettingsSubshell.kt` - Replace function calls, add import
4. `/src/main/kotlin/Util/Util.kt` - Add deprecation annotation only

## TESTING CHECKLIST

After implementation, verify:
- [ ] Single-line input works normally
- [ ] Multi-line JSON input is detected and collected
- [ ] @clip macro expands clipboard content
- [ ] @context macro expands story context
- [ ] @lore macro expands lorebook
- [ ] @file macro reads file content
- [ ] Large paste operations (>1000 chars) trigger multi-line mode
- [ ] "END", "c", or "n" terminates multi-line input
- [ ] Auto-detection of complete JSON structures

## CRITICAL SUCCESS CRITERIA

1. **Zero Breaking Changes** - All existing functionality must work
2. **Single Function** - Only `readEnhancedInput()` should be used going forward
3. **Minimal Code** - No unnecessary features or verbose implementations
4. **Backward Compatible** - Old functions still work but are deprecated

## IMPLEMENTATION ORDER

1. Create EnhancedInput.kt with exact code above
2. Test the new function in isolation
3. Update Shell.kt imports and function calls
4. Update WriterSubshell.kt imports and function calls
5. Update SettingsSubshell.kt imports and function calls
6. Add deprecation annotations to old functions
7. Test entire system for regressions

**DO NOT** implement any features not explicitly listed above. **DO NOT** add configuration systems, validation, or other complex features. The goal is minimal, working code that solves the immediate problem.
## VALIDATION STEPS

After completing implementation:

1. **Compile Check**: Ensure project compiles without errors
2. **Function Test**: Test each macro type (@clip, @context, @lore, @file)
3. **Multi-line Test**: Paste JSON and verify auto-detection
4. **Integration Test**: Verify all shell commands still work
5. **Regression Test**: Ensure no existing functionality is broken

## COMPLETION CRITERIA

✅ **DONE** when:
- Single `readEnhancedInput()` function handles all input scenarios
- All shell files use the new function
- Old functions are deprecated but still work
- No new bugs introduced
- All existing macros and multi-line detection work as before

**STOP** implementing when these criteria are met. Do not add additional features.