package lab.agent;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 面试官 Agent（草图）：每一轮由模型在"追问 / 下一题 / 收尾"之间做决策，决策过程中可以调用工具。
 *
 * <p>和项目现有语音面试的差别：人设放在系统提示词里（可信内容不包数据边界）；
 * 对话记录放在用户消息里并包数据边界（不可信内容）；工具循环依赖框架自动注册的
 * ToolCallingAdvisor（保留完整历史）；再用 LoopGuardAdvisor 加一道硬上限；
 * 输出是结构化的决策，而不是一段自由文本。
 */
public final class InterviewerAgent {

  public enum Action {
    FOLLOW_UP, NEXT_QUESTION, WRAP_UP
  }

  public record NextAction(Action action, String utterance, String reason) {
  }

  static final String WRAP_UP_JSON =
      "{\"action\":\"WRAP_UP\",\"utterance\":\"好的，这道题我们先聊到这里。\",\"reason\":\"LOOP_GUARD\"}";

  private static final String DECISION_RULES = """

      # 决策规则
      - 回答覆盖了参考要点的大部分：NEXT_QUESTION；遗漏关键点或停留在概念：FOLLOW_UP，追问一个具体细节。
      - 先用 lookupReference 对照要点，再用 recordObservation 记录证据和分数，然后输出决策。
      - 同一轮最多调用 3 次工具；收到 BUDGET_EXHAUSTED 后必须直接输出决策。
      - utterance 是要说给候选人的一句话，口语化，不超过 60 字。
      """;

  private final ChatClient chatClient;

  public InterviewerAgent(ChatModel chatModel, int maxModelCallsPerTurn) {
    this.chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(new LoopGuardAdvisor(maxModelCallsPerTurn, WRAP_UP_JSON))
        .build();
  }

  public NextAction decide(String sessionId, String persona, String transcript, InterviewerTools tools) {
    return chatClient.prompt()
        .system(persona + DECISION_RULES)
        .user(transcript)
        .tools(tools)
        .toolContext(Map.of("sessionId", sessionId))
        .advisors(LoopGuardAdvisor.newBudget())
        .call()
        .entity(NextAction.class);
  }
}
