# Astro Survey Atlas Warehouse（中文）

英文文档：[README.md](README.md)

这是一个面向天文学的空间目录服务，用于发现公开或已配置的数据文件。
Warehouse 枚举本地、S3 兼容和 OSS 数据源，从元数据提取文件级天空覆盖，
并维护可查询的 CoverageLayer 当前状态。科学数据仍由用户从原始来源下载。

## 产品边界

v1 只围绕以下领域对象展开：

- `CoverageLayer`：一个 survey、release 和 product 组成的当前刷新单元。
- `FileAsset`：一个实际发现的文件，ID 是 canonical URI 的稳定哈希。
- `SpatialCoverage`：带有方法、角色和精度的 ICRS/NESTED HEALPix `order/ipix` 关联。
- `ExtractionMode`：扫描输入声明的空间语义。
- `SourceSnapshot`：一次扫描的带哈希 inventory 和 evidence。
- `ScanRequest`：Kubernetes 提交对象及可观察的执行状态。

Warehouse 不是工作流引擎、科学数据归约管线、原始数据代理、通用 catalog
或下载服务。`SourceUnit` 仅保留为未来概念，v1 尚未实现。

## 架构说明

架构图和英文主链路见 [README.md](README.md#architecture)：Assets 提交
ScanRequest/ScanPlan v2，Thin Operator 创建 Scanner Job；Scanner 通过
本地/S3/OSS connector 枚举文件，由 CoverageExtractor 读取 FITS header 或
catalog 空间列，同时生成 evidence，最后写入三个 `ast_*` 当前状态索引。
Assets 生产环境直接读取这些索引，`query-api` 只承担只读诊断职责。
Kubernetes Secret 只投影凭据引用，绝不把凭据值放进 plan、evidence、日志或
查询响应。

## 模块

| 模块 | 职责 |
| --- | --- |
| `spatial-core` | 领域类型、ScanPlan v2 校验、HEALPix 规则和读写接口。 |
| `scanner-cli` | 本地/S3/OSS 枚举、FITS/catalog 提取、evidence 和扫描执行。 |
| `index-elasticsearch` | 严格 mapping、lease、当前 layer 替换、有界 bulk 写入和读取。 |
| `query-api` | 只读 point、cone 和显式 order HEALPix 诊断接口。 |
| `operator` | namespaced `ScanRequest` 校验、Secret 投影、Job 和状态报告。 |
| `moc-discovery-cli` | 受控 MOC discovery evidence Job，不写入 `ast_*`。 |

## 扫描契约

ScanPlan v2 声明一个 source、一个 layer、一个 extraction mode、一个 index
sink 和一个 evidence 路径。支持的模式为：

- `fits-wcs`：只从 FITS header 采样受支持的线性 TAN WCS，结果精度为 `estimated`。
- `fits-header-position`：把显式 FITS header 坐标记录为 `entrypoint-only`。
- `catalog-radec`：读取配置的 ICRS RA/Dec 列，生成去重且 `exact` 的覆盖 cell。
- `catalog-healpix`：保留来源显式声明的 NESTED order 和 pixel。

FITS 不读取科学数组；catalog 按文件创建 `FileAsset`，不会按行创建索引文档。
所有 plan 必须在 source 枚举和凭据 I/O 之前完成校验。

Coverage 保留实际 order 和 precision。可以把细粒度 cell 合并到更粗 order，
但不能把粗 cell 扩展成虚假的细粒度覆盖。响应限制通过 `truncated` 单独报告。

## 当前状态索引

Warehouse 只拥有以下新索引：

```text
ast_layer_index_v1
ast_file_index_v1
ast_coverage_index_v1
```

`v1` 表示 mapping/contract 版本，不表示扫描运行次数。Layer 刷新状态依次
使用 `UPDATING`、`ACTIVE` 或 `FAILED`；只有 `ACTIVE` 可查询。失败或部分覆盖
不能伪装成成功的空结果。FileAsset ID 是全局 canonical URI 哈希，coverage edge
按 layer 替换。旧 `astro_*` 索引和冻结的 legacy 仓库不作为运行时 fallback。

## Evidence 与安全

持久化扫描把 inventory、source hash、normalized summary 以及提取/写入错误写入
显式 PVC 或由对象存储支持的 evidence mount。凭据只能通过 Kubernetes Secret
或环境引用表达，不能出现在 plan、evidence、日志、索引或查询响应中。原始文件
和科学数组不会经过 Warehouse。

## 构建与运行

在仓库根目录执行完整 Maven 验证：

```bash
mvn test
mvn package
```

不连接 Elasticsearch，运行本地诊断扫描：

```bash
java -jar scanner-cli/target/scanner-cli-0.1.0-SNAPSHOT-runner.jar \
  --plan /path/to/scan-plan.json --memory
```

持久化 Kubernetes 扫描按 namespace、CRD、RBAC、Operator Deployment、evidence
PVC、credential Secret、ScanRequest 的顺序部署。完整示例见
[`deploy/kubernetes/README.md`](deploy/kubernetes/README.md) 和
[`deploy/helm/atlas-warehouse-infra`](deploy/helm/atlas-warehouse-infra)。

基础设施 chart 默认只安装 Elasticsearch 和 MinIO。Kafka 是可选依赖，使用
`--set kafka.enabled=true` 显式开启；当前 Scanner/Operator 直接写
Elasticsearch 和 evidence，不依赖 Kafka。未来如果引入事件驱动或 Flink
部署 profile，可以在不改变 ScanPlan 契约的前提下启用 Kafka。

## 契约与延伸阅读

- [`HANDOFF.md`](HANDOFF.md)：运维续接点和部署说明。
- [`CONTEXT.md`](CONTEXT.md)：统一领域词汇。
- [`docs/requirements.md`](docs/requirements.md)：产品需求和完成标准。
- [`docs/architecture.md`](docs/architecture.md)：模块边界和刷新流程。
- [`docs/scan-plan.md`](docs/scan-plan.md)：ScanPlan v2 输入及校验契约。
- [`docs/index-contract.md`](docs/index-contract.md)：Elasticsearch 文档和空间语义。
- [`docs/query-api.md`](docs/query-api.md)：诊断查询和分页契约。
- [`docs/operator.md`](docs/operator.md)：ScanRequest、Job、evidence 和 Secret 引用契约。
