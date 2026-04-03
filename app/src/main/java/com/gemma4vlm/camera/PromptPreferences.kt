package com.gemma4vlm.camera

import android.content.Context

object PromptPreferences {

    const val DEFAULT_SYSTEM_INSTRUCTION =
        "You are a real-time camera assistant. " +
        "Describe what you see in the image concisely in 1-2 sentences. " +
        "Focus on the main objects, people, and actions visible. " +
        "Be direct and specific."

    const val DEFAULT_FRAME_PROMPT = "What do you see?"

    private const val PREFS_NAME = "gemma4vlm_prefs"
    private const val KEY_SYSTEM_INSTRUCTION = "system_instruction"
    private const val KEY_FRAME_PROMPT = "frame_prompt"

    fun load(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val systemInstruction = prefs.getString(KEY_SYSTEM_INSTRUCTION, DEFAULT_SYSTEM_INSTRUCTION)
            ?: DEFAULT_SYSTEM_INSTRUCTION
        val framePrompt = prefs.getString(KEY_FRAME_PROMPT, DEFAULT_FRAME_PROMPT)
            ?: DEFAULT_FRAME_PROMPT
        return systemInstruction to framePrompt
    }

    fun save(context: Context, systemInstruction: String, framePrompt: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SYSTEM_INSTRUCTION, systemInstruction)
            .putString(KEY_FRAME_PROMPT, framePrompt)
            .apply()
    }
}
