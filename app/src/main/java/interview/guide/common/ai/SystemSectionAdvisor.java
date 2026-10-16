package interview.guide.common.ai;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

/**
 * 把一段文本追加到系统提示词末尾的请求级 Advisor。
 * 多个实例按 order 升序依次执行，用来在 Advisor 链上分段组装系统提示词
 * （角色、简历、JD、安全约束等），调用方不再手工拼接完整的 system 字符串。
 * order 应大于 SafeGuardAdvisor（100），避免简历、JD 里的敏感词触发整轮拒答。
 */
public class SystemSectionAdvisor implements BaseAdvisor {

  private final String name;
  private final int order;
  private final String section;

  public SystemSectionAdvisor(String name, int order, String section) {
    this.name = name;
    this.order = order;
    this.section = section;
  }

  @Override
  public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
    return request.mutate()
        .prompt(request.prompt().augmentSystemMessage(section))
        .build();
  }

  @Override
  public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
    return response;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getOrder() {
    return order;
  }
}
