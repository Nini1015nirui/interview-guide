package lab.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * 输出侧护栏（草图）：检查模型最终"说了什么"，而不是拿关键词去扫用户数据。
 *
 * <p>order 比 ToolCallingAdvisor 小，位于工具循环外层，只看到最终回答。
 * 命中"角色被劫持"的话术或系统提示词里的独有标记（说明提示词被复述泄露）时，替换成安全回复并计数。
 */
public final class OutputGuardAdvisor implements CallAdvisor {

  private final List<String> hijackSignals;
  private final List<String> secretMarkers;
  private final String safeReply;
  private final AtomicInteger blocked = new AtomicInteger();

  public OutputGuardAdvisor(List<String> hijackSignals, List<String> secretMarkers, String safeReply) {
    this.hijackSignals = hijackSignals;
    this.secretMarkers = secretMarkers;
    this.safeReply = safeReply;
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    ChatClientResponse response = chain.nextCall(request);
    ChatResponse chat = response.chatResponse();
    if (chat == null || chat.getResult() == null || chat.hasToolCalls()) {
      return response;
    }
    String text = chat.getResult().getOutput().getText();
    if (text != null && (containsAny(text, hijackSignals) || containsAny(text, secretMarkers))) {
      blocked.incrementAndGet();
      ChatResponse replaced = ChatResponse.builder()
          .from(chat)
          .generations(List.of(new Generation(new AssistantMessage(safeReply))))
          .build();
      return response.mutate().chatResponse(replaced).build();
    }
    return response;
  }

  public int blockedCount() {
    return blocked.get();
  }

  private static boolean containsAny(String text, List<String> needles) {
    return needles.stream().anyMatch(text::contains);
  }

  @Override
  public String getName() {
    return "OutputGuardAdvisor";
  }

  @Override
  public int getOrder() {
    return ToolCallingAdvisor.DEFAULT_ORDER - 50;
  }
}
