package Shell

import Globals.Env
import Globals.Prompts
import readEnhancedInput

/**
 * Persona subshell for assigning roles to character keys.
 */
fun personaSubshell() {
    println("\n=== Persona Assignment Subshell ===")
    
    while (true) {
        displayCurrentMappings()
        println("\nSelect a role to assign a persona:")
        println("1. Author")
        println("2. Editor")
        println("3. Richard Treadwell (Reviewer)")
        println("4. Writing Control (Invis)")
        println("5. Back to main shell")
        
        print("persona> ")
        val choice = readEnhancedInput().trim()
        
        when (choice) {
            "1" -> assignPersonaToRole("Author")
            "2" -> assignPersonaToRole("Editor")
            "3" -> assignPersonaToRole("Richard Treadwell")
            "4" -> assignPersonaToRole("Control")
            "5", "back", "exit" -> return
            else -> println("Invalid choice. Please select 1-5.")
        }
    }
}

private fun displayCurrentMappings() {
    println("\nCurrent Role Mappings:")
    println("Author: ${Env.activeAuthorPersona}")
    println("Editor: ${Env.activeEditorPersona}")
    println("Richard Treadwell: ${Env.activeRichardTreadwellPersona}")
    println("Writing Control: ${Env.activeControlPersona}")
}

private fun assignPersonaToRole(role: String) {
    val personas = Prompts.promptMap.keys.toList().sorted()
    println("\nAvailable Personas for $role:")
    personas.forEachIndexed { index, persona ->
        println("${index + 1}. $persona")
    }
    println("${personas.size + 1}. Cancel")
    
    print("Select a persona (1-${personas.size + 1}): ")
    val choice = readEnhancedInput().toIntOrNull()
    
    if (choice != null && choice in 1..personas.size) {
        val selectedPersona = personas[choice - 1]
        when (role) {
            "Author" -> Env.activeAuthorPersona = selectedPersona
            "Editor" -> Env.activeEditorPersona = selectedPersona
            "Richard Treadwell" -> Env.activeRichardTreadwellPersona = selectedPersona
            "Control" -> Env.activeControlPersona = selectedPersona
        }
        println("Assigned $selectedPersona to $role.")
        
        val currentSettings = loadSettings()
        val newSettings = currentSettings.copy(
            activeAuthorPersona = Env.activeAuthorPersona,
            activeEditorPersona = Env.activeEditorPersona,
            activeRichardTreadwellPersona = Env.activeRichardTreadwellPersona,
            activeControlPersona = Env.activeControlPersona
        )

        applyRuntimeSettings(newSettings)
        println("Pipelines rebuilt with new prompts.")
    } else if (choice == personas.size + 1) {
        println("Assignment cancelled.")
    } else {
        println("Invalid selection.")
    }
}
