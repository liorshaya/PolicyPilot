package com.liorshaya.policypilot.ai.service.chat;

/** Arguments a tool refuses; the message says what was wrong, and the model reads it in the tool's result. */
public class ToolArgumentException extends ToolRefusedException {

    public ToolArgumentException(String message) {
        super("invalid_arguments", message);
    }
}
