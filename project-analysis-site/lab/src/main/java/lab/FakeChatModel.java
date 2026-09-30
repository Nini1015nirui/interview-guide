package lab;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * 脚本化的假模型：不联网，按给定函数决定每一轮返回什么，并记录它收到的每一个 Prompt。
 * 用它可以精确观察 ChatClient / Advisor / 工具循环到底把哪些消息发给了模型。
 */
public final class FakeChatModel implements ChatModel {

  private final Function<Prompt, AssistantMessage> script;
  private final List<Prompt> received = new ArrayList<>();

  public FakeChatModel(Function<Prompt, AssistantMessage> script) {
    this.script = script;
  }

  /** 第一轮请求一次工具，拿到工具结果后给出最终回答。 */
  public static FakeChatModel callsToolOnce(String toolName, String argumentsJson, String finalAnswer) {
    return new FakeChatModel(prompt -> lastMessageIsToolResponse(prompt)
        ? AssistantMessage.builder().content(finalAnswer).build()
        : toolCall("call_1", toolName, argumentsJson));
  }

  /** 永远请求工具，用来验证工具循环的终止保护。 */
  public static FakeChatModel alwaysCallsTool(String toolName, String argumentsJson) {
    int[] seq = {0};
    return new FakeChatModel(prompt -> toolCall("call_" + (++seq[0]), toolName, argumentsJson));
  }

  /** 永远返回同一段文本。 */
  public static FakeChatModel alwaysReplies(String text) {
    return new FakeChatModel(prompt -> AssistantMessage.builder().content(text).build());
  }

  public static AssistantMessage toolCall(String id, String toolName, String argumentsJson) {
    return AssistantMessage.builder()
        .content("")
        .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", toolName, argumentsJson)))
        .build();
  }

  public static boolean lastMessageIsToolResponse(Prompt prompt) {
    List<Message> messages = prompt.getInstructions();
    return !messages.isEmpty() && messages.get(messages.size() - 1) instanceof ToolResponseMessage;
  }

  @Override
  public ChatOptions getOptions() {
    // 必须是 ToolCallingChatOptions，ChatClient 才会把工具定义合并进请求
    return ToolCallingChatOptions.builder().build();
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    received.add(prompt);
    return new ChatResponse(List.of(new Generation(script.apply(prompt))));
  }

  public List<Prompt> received() {
    return received;
  }

  public int calls() {
    return received.size();
  }
}
