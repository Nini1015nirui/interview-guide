package lab.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * 工具循环的硬性终止保护。
 *
 * <p>order 比 ToolCallingAdvisor 大，位于工具循环内部，每一轮迭代（每一次真正请求模型）都会经过它。
 * 超过上限后不再请求模型，直接返回一个不含 tool_calls 的收尾结果，循环因此结束。
 * 计数器通过 advisor 参数按请求传入：上下文在每轮迭代时被复制，但复制的是同一个计数器引用。
 */
public final class LoopGuardAdvisor implements CallAdvisor {

  public static final String COUNTER_KEY = "lab.loop-guard.counter";

  private final int maxModelCalls;
  private final String fallbackContent;

  public LoopGuardAdvisor(int maxModelCalls, String fallbackContent) {
    this.maxModelCalls = maxModelCalls;
    this.fallbackContent = fallbackContent;
  }

  /** 每次 prompt() 调用时传入一个新预算。 */
  public static Consumer<ChatClient.AdvisorSpec> newBudget() {
    return spec -> spec.param(COUNTER_KEY, new AtomicInteger());
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    if (request.context().get(COUNTER_KEY) instanceof AtomicInteger calls
        && calls.incrementAndGet() > maxModelCalls) {
      AssistantMessage stop = AssistantMessage.builder().content(fallbackContent).build();
      return ChatClientResponse.builder()
          .chatResponse(new ChatResponse(List.of(new Generation(stop))))
          .context(request.context())
          .build();
    }
    return chain.nextCall(request);
  }

  @Override
  public String getName() {
    return "LoopGuardAdvisor";
  }

  @Override
  public int getOrder() {
    return ToolCallingAdvisor.DEFAULT_ORDER + 10;
  }
}
