package lab.agent;

import java.util.function.Consumer;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 记录每一次模型请求的 token 用量（含缓存命中的输入 token），按 Provider 和业务场景打标签。
 *
 * <p>放在工具循环内部（order 比 ToolCallingAdvisor 大），工具调用引起的每一次额外往返都会被计入，
 * 这样才能回答"开了工具之后，一次语音回答到底多花了多少 token"。
 */
public final class TokenUsageAdvisor implements CallAdvisor {

  public static final String SCENE_KEY = "lab.usage.scene";
  public static final String METRIC = "app.ai.tokens";

  private final MeterRegistry registry;
  private final String provider;

  public TokenUsageAdvisor(MeterRegistry registry, String provider) {
    this.registry = registry;
    this.provider = provider;
  }

  public static Consumer<ChatClient.AdvisorSpec> scene(String scene) {
    return spec -> spec.param(SCENE_KEY, scene);
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    ChatClientResponse response = chain.nextCall(request);
    ChatResponse chat = response.chatResponse();
    if (chat == null) {
      return response;
    }
    Usage usage = chat.getMetadata().getUsage();
    String scene = String.valueOf(request.context().getOrDefault(SCENE_KEY, "unknown"));
    record(scene, "prompt", usage.getPromptTokens());
    record(scene, "completion", usage.getCompletionTokens());
    Long cached = usage.getCacheReadInputTokens();
    record(scene, "cache_read", cached == null ? null : cached.intValue());
    registry.counter("app.ai.model_calls", "provider", provider, "scene", scene).increment();
    return response;
  }

  private void record(String scene, String type, Integer tokens) {
    if (tokens != null && tokens > 0) {
      registry.counter(METRIC, "provider", provider, "scene", scene, "type", type).increment(tokens);
    }
  }

  @Override
  public String getName() {
    return "TokenUsageAdvisor";
  }

  @Override
  public int getOrder() {
    return ToolCallingAdvisor.DEFAULT_ORDER + 20;
  }
}
