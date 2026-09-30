package lab.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 面试官 Agent 的工具集（草图）：两个只读工具 + 一个受控的写工具，每轮共享一个调用预算。
 *
 * <p>设计要点：工具粒度按"面试官真实会做的动作"划分；只读工具可以放心重试；
 * 唯一的写工具是幂等的（同一题重复记录以最后一次为准）；返回值都做了截断，避免撑爆上下文；
 * 预算耗尽时返回明确的机器可读信号，而不是抛异常。
 */
public class InterviewerTools {

  public static final String BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED: 本轮工具预算已用完，请直接给出下一步动作";

  /** 类目 → 参考要点。生产中来自 Skill 的 references 或题库题目的 keyPoints。 */
  public interface ReferenceStore {
    String keyPoints(String category);
  }

  /** 知识库检索。生产中复用 KnowledgeBaseVectorService.similaritySearch。 */
  public interface KnowledgeSearch {
    List<String> search(String query, int topK);
  }

  public record Observation(String sessionId, int questionIndex, String evidence, int score) {
  }

  private final ReferenceStore references;
  private final KnowledgeSearch knowledge;
  private final AtomicInteger budget;
  private final Map<Integer, Observation> observations = new ConcurrentHashMap<>();

  public InterviewerTools(ReferenceStore references, KnowledgeSearch knowledge, int budgetPerTurn) {
    this.references = references;
    this.knowledge = knowledge;
    this.budget = new AtomicInteger(budgetPerTurn);
  }

  @Tool(description = "查询某个考察类目的参考要点，用来判断候选人的回答遗漏了什么。只读，不要用它出新题。")
  public String lookupReference(
      @ToolParam(description = "类目 key，例如 REDIS、RAG、MCP_PROTOCOL") String category) {
    if (!spend()) {
      return BUDGET_EXHAUSTED;
    }
    String points = references.keyPoints(category);
    return points == null ? "NOT_FOUND: 没有该类目的参考要点" : truncate(points, 1500);
  }

  @Tool(description = "在候选人选择的知识库中检索相关片段（最多 3 段），用于基于资料追问。只读。")
  public String searchKnowledgeBase(@ToolParam(description = "检索语句，写成完整的问题") String query) {
    if (!spend()) {
      return BUDGET_EXHAUSTED;
    }
    List<String> hits = knowledge.search(query, 3);
    if (hits.isEmpty()) {
      return "NO_HIT";
    }
    return String.join("\n---\n", hits.stream().map(h -> truncate(h, 600)).toList());
  }

  @Tool(description = "记录对当前题目的评估证据和 0-100 分，同一题重复记录时以最后一次为准。")
  public String recordObservation(
      @ToolParam(description = "题目序号，从 0 开始") int questionIndex,
      @ToolParam(description = "引用候选人原话的证据，50 字以内") String evidence,
      @ToolParam(description = "0 到 100 的整数分") int score,
      ToolContext toolContext) {
    if (!spend()) {
      return BUDGET_EXHAUSTED;
    }
    if (score < 0 || score > 100) {
      return "INVALID_ARGUMENT: score 必须在 0-100 之间";
    }
    String sessionId = String.valueOf(toolContext.getContext().get("sessionId"));
    observations.put(questionIndex, new Observation(sessionId, questionIndex, truncate(evidence, 80), score));
    return "OK";
  }

  public Map<Integer, Observation> observations() {
    return observations;
  }

  public int budgetLeft() {
    return Math.max(0, budget.get());
  }

  private boolean spend() {
    return budget.getAndDecrement() > 0;
  }

  private static String truncate(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max) + "…";
  }
}
