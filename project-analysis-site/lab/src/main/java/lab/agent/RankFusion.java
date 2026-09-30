package lab.agent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 倒数排名融合（Reciprocal Rank Fusion）：把向量检索和关键词检索各自的排序合成一个排序。
 *
 * <p>只看名次、不看分数，所以不需要把余弦相似度和全文检索得分归一化到同一量纲。
 * 常数 k 用来削弱头部名次的权重，常见取值 60。
 */
public final class RankFusion {

  private RankFusion() {
  }

  @SafeVarargs
  public static List<String> fuse(int k, int topN, List<String>... rankings) {
    Map<String, Double> scores = new HashMap<>();
    for (List<String> ranking : rankings) {
      for (int rank = 0; rank < ranking.size(); rank++) {
        scores.merge(ranking.get(rank), 1.0 / (k + rank + 1), Double::sum);
      }
    }
    return scores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())
            .thenComparing(Map.Entry.comparingByKey()))
        .limit(topN)
        .map(Map.Entry::getKey)
        .toList();
  }
}
