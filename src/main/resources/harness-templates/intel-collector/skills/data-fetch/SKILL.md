---
name: data-fetch
description: 使用 Shell 工具拉取权威数据源的公开数据
---

# 数据拉取技能

## 使用场景

当需要获取权威机构的公开数据时，使用本技能执行 Shell 命令拉取。

## 常用命令

### 拉取 JSON 数据

```bash
curl -s "https://www.pbc.gov.cn/api/data" | python3 -m json.tool
```

### 拉取网页内容

```bash
curl -s "https://www.example.com/notice" | grep -A 10 "关键词"
```

### 下载文件

```bash
curl -O "https://www.example.com/report.pdf"
```

### 处理 CSV 数据

```bash
cat data.csv | awk -F, '{print $1,$2}' | head -20
```

## 权威数据源

**中国人民银行**：
- 利率数据：https://www.pbc.gov.cn/zhengfuxinxi/shuju/

**国家金融监督管理总局**：
- 政策文件：https://www.nfra.gov.cn/

**中国证监会**：
- 公告通知：https://www.csrc.gov.cn/csrc/

## 注意事项

1. 仅拉取公开数据，不尝试访问需认证的接口
2. 使用 `-s` 参数避免 curl 输出进度信息
3. 大文件只拉取前 N 行（使用 `head`）
4. JSON 数据用 python 格式化便于解析
