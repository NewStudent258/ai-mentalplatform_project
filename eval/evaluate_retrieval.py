# -*- coding: utf-8 -*-
"""
知识库检索质量评测。

用法：
    python eval/evaluate_retrieval.py

设计要点：
  1. 通过后端接口跑检索，测的是**真实实现**而不是另写一套逻辑——
     后者测不出真实问题（比如阈值过滤、结果截断这些细节）。
  2. 除总体指标外，**按查询类型拆分**。总体 85% 看不出问题，
     拆开才能发现「精确匹配类只有 40%」这样的薄弱环节。
  3. 输出每条未命中的 query，便于逐条分析原因。
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict

BASE = "http://localhost:1236/api"
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin123456"

# 评测参数
TOP_K = 5

EVALSET_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "retrieval-evalset.json")


def login():
    body = json.dumps({"username": ADMIN_USER, "password": ADMIN_PASSWORD}).encode("utf-8")
    req = urllib.request.Request(BASE + "/user/login", data=body,
                                 headers={"Content-Type": "application/json; charset=UTF-8"})
    resp = json.loads(urllib.request.urlopen(req, timeout=20).read().decode("utf-8"))
    if resp.get("code") != "200":
        raise RuntimeError("登录失败：%s" % resp.get("msg"))
    return resp["data"]["token"]


def search(query, token, top_k=TOP_K, mode=None):
    """调用真实检索接口，返回排序后的文章ID列表

    mode: VECTOR / KEYWORD / HYBRID，None 表示用服务端默认值
    """
    # 注意 BASE 已包含 /api，这里不要再拼一次
    params = {"query": query, "topK": top_k}
    if mode:
        params["mode"] = mode
    qs = urllib.parse.urlencode(params)
    req = urllib.request.Request(BASE + "/knowledge/search?" + qs,
                                 headers={"Token": token})
    try:
        resp = json.loads(urllib.request.urlopen(req, timeout=30).read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print("  检索请求失败 %s: %s" % (query, e))
        return []
    if resp.get("code") != "200":
        return []
    return [item["id"] for item in (resp.get("data") or [])]


def evaluate(mode=None):
    """mode: VECTOR / KEYWORD / HYBRID，None 用服务端默认值"""
    evalset = json.load(open(EVALSET_PATH, encoding="utf-8"))
    queries = evalset["queries"]
    articles = evalset["articles"]

    print("评测集：%d 条查询，覆盖 %d 篇文章，Top-%d\n" % (len(queries), len(articles), TOP_K))
    print("正在登录...")
    token = login()
    print("开始评测（每条查询都会调用一次 Embedding，请稍候）...\n")

    results = []
    misses = []

    for i, item in enumerate(queries, 1):
        qid = item["id"]
        query = item["query"]
        expected = set(item["expected"])
        qtype = item["type"]

        hit_ids = search(query, token, mode=mode)
        hit_set = set(hit_ids)

        # HitRate@K：前 K 个结果里是否至少有 1 篇期望文章（召回能力）
        hit = len(expected & hit_set) > 0
        # HitRate@1：第一篇就是期望文章（排序能力，比 HitRate@K 严格得多）
        hit_at_1 = bool(hit_ids) and hit_ids[0] in expected
        # MRR：第一个命中结果排在第几位（取倒数）
        rank = next((idx + 1 for idx, aid in enumerate(hit_ids) if aid in expected), 0)
        rr = 1.0 / rank if rank else 0.0

        results.append({"id": qid, "type": qtype, "hit": hit, "hit1": hit_at_1, "rr": rr,
                        "query": query, "expected": sorted(expected), "got": hit_ids})

        if not hit:
            misses.append(results[-1])

        # 进度提示：100 条查询耗时不短，让用户看到进展
        if i % 20 == 0:
            print("  已完成 %d/%d" % (i, len(queries)))

    # ===== 总体指标 =====
    total = len(results)
    hit_rate = sum(r["hit"] for r in results) / total
    hit1_rate = sum(r["hit1"] for r in results) / total
    mrr = sum(r["rr"] for r in results) / total

    print("\n" + "=" * 62)
    print("总体结果")
    print("=" * 62)
    print("  HitRate@%d : %.2f%%  (%d/%d)" % (TOP_K, hit_rate * 100,
                                               sum(r["hit"] for r in results), total))
    print("  HitRate@1  : %.2f%%  ← 排序质量，比 HitRate@%d 严格得多" % (hit1_rate * 100, TOP_K))
    print("  MRR@%d     : %.4f" % (TOP_K, mrr))
    print("  （HitRate@%d 已接近饱和时，应以 HitRate@1 与 MRR 判断优化空间）" % TOP_K)

    # ===== 按类型拆分（最有价值的部分）=====
    print("\n按查询类型拆分：")
    print("  %-16s %6s %10s %10s %8s" % ("类型", "条数", "HitRate@%d" % TOP_K, "HitRate@1", "MRR"))
    by_type = defaultdict(list)
    for r in results:
        by_type[r["type"]].append(r)
    for qtype, items in sorted(by_type.items(),
                               key=lambda kv: -sum(r["hit1"] for r in kv[1]) / len(kv[1])):
        t_hit = sum(r["hit"] for r in items) / len(items)
        t_hit1 = sum(r["hit1"] for r in items) / len(items)
        t_mrr = sum(r["rr"] for r in items) / len(items)
        print("  %-16s %6d %9.2f%% %9.2f%% %8.4f" % (qtype, len(items), t_hit * 100, t_hit1 * 100, t_mrr))

    # ===== 未命中明细（用于逐条分析）=====
    if misses:
        print("\n未命中 %d 条：" % len(misses))
        for r in misses:
            exp_titles = ", ".join(articles.get(str(a), "?") for a in r["expected"])
            got_titles = ", ".join(articles.get(str(a), "?") for a in r["got"][:3])
            print("  [%s] %s" % (r["type"], r["query"]))
            print("      期望: %s" % exp_titles)
            print("      实得: %s" % (got_titles or "无结果"))

    # 保存明细，便于对比不同方案
    out_path = os.path.join(os.path.dirname(EVALSET_PATH), "eval-result-%s.json" % (mode or "default").lower())
    json.dump({"hit_rate": hit_rate, "hit1_rate": hit1_rate, "mrr": mrr, "top_k": TOP_K,
               "by_type": {t: {"hit_rate": sum(r["hit"] for r in v) / len(v),
                               "hit1_rate": sum(r["hit1"] for r in v) / len(v),
                               "mrr": sum(r["rr"] for r in v) / len(v),
                               "count": len(v)} for t, v in by_type.items()},
               "details": results},
              open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("\n明细已保存：%s" % out_path)


if __name__ == "__main__":
    # 用法: python eval/evaluate_retrieval.py [VECTOR|KEYWORD|HYBRID]
    arg = sys.argv[1].upper() if len(sys.argv) > 1 else None
    evaluate(arg)
