package com.eyki.offerpilot.aicore.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Advisor that appends a "re-reading" instruction to the system prompt. Improves reasoning quality by asking the model to
 * read the question twice before answering.
 *
 * <p>Implemented via {@link Prompt#augmentSystemMessage(java.util.function.Function)}: if a system message exists it is
 * transformed in place; otherwise a new one is prepended — preserving the original message order and request context
 * (copied via {@code request.mutate()}).</p>
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ReReadingAdvisor.class);

    private static final String RE_READING_INSTRUCTION = """
        \n\nIMPORTANT: Before answering, please read the user's question carefully.
        Then re-read it to ensure you understand the full context and requirements.
        If you need to use any tools or references, do so before composing your final answer.
        """;

    private static final int ORDER = 1;

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(appendSystemInstruction(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(appendSystemInstruction(request));
    }

    /**
     * Appends the re-reading instruction to the system message via {@link Prompt#augmentSystemMessage(java.util.function.Function)}.
     * Uses {@code request.mutate()} to copy all existing request state (context, etc.) before modifying the prompt.
     */
    private ChatClientRequest appendSystemInstruction(ChatClientRequest request) {
        Prompt modifiedPrompt = request.prompt().augmentSystemMessage(
            sm -> new SystemMessage(sm.getText() + RE_READING_INSTRUCTION));
        return request.mutate().prompt(modifiedPrompt).build();
    }
}
