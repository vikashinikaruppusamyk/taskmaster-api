package com.vikashini.taskmaster.ai;

/**
 * Turns a prompt into text. The real implementation calls Claude; tests use a fake so
 * they never need an API key or the network.
 */
public interface TextGenerator {

    /**
     * @param instructions how to respond (the system prompt)
     * @param input        what to respond to; may contain user-written text
     * @return the generated text
     * @throws AiUnavailableException when the model cannot be reached or declines
     */
    String generate(String instructions, String input);
}
