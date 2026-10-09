package com.edgeore.litertlm;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.SamplerConfig;

import java.util.Collections;

/**
 * Thin bridge to LiteRT-LM (com.google.ai.edge.litertlm, Apache-2.0), the on-device LLM runtime used by
 * Google AI Edge Gallery. Java-only module on purpose: litertlm-android 0.8.0 carries Kotlin 2.2 metadata,
 * which the project's pinned Kotlin 2.0.21 compiler refuses to read. LiteRT-LM is an implementation
 * dependency of this module, so it never reaches the app's Kotlin compile classpath, and nothing in the
 * pinned toolchain has to move. Public signatures use JDK types only.
 *
 * CPU backend only. The gallery prefers GPU, which needs OpenCL native-library declarations and per-device
 * qualification that this build has not done.
 */
public final class LiteRtLmBridge implements AutoCloseable {
    private final Engine engine;
    private volatile Conversation active;

    /** Loads the model. Blocking and slow (seconds); call off the main thread. Throws if the file is not a supported model. */
    public LiteRtLmBridge(String modelPath, int maxTokens, String cacheDirOrNull) {
        engine = new Engine(new EngineConfig(modelPath, Backend.CPU, null, null, maxTokens > 0 ? maxTokens : null, cacheDirOrNull));
        engine.initialize();
    }

    /** One single-turn generation in a fresh conversation. Blocking. */
    public String generate(String systemPrompt, String prompt, int topK, double topP, double temperature) {
        Message system = (systemPrompt == null || systemPrompt.isEmpty()) ? null : Message.Companion.of(systemPrompt);
        ConversationConfig config = new ConversationConfig(system, Collections.emptyList(), new SamplerConfig(topK, topP, temperature, 0));
        Conversation c = engine.createConversation(config);
        active = c;
        try {
            Message reply = c.sendMessage(Message.Companion.of(prompt));
            StringBuilder sb = new StringBuilder();
            for (Content part : reply.getContents()) {
                if (part instanceof Content.Text) sb.append(((Content.Text) part).getText());
            }
            return sb.toString();
        } finally {
            active = null;
            c.close();
        }
    }

    /** Asks the runtime to stop the in-flight generation. Returns false when nothing was running. */
    public boolean cancel() {
        Conversation c = active;
        if (c == null) return false;
        c.cancelProcess();
        return true;
    }

    @Override
    public void close() {
        engine.close();
    }
}
