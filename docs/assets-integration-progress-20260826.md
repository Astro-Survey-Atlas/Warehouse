# Warehouse 与 Assets 进度对齐

更新日期：2026-08-28

本文可以直接转给 Assets 团队。集群数量和 Job 状态是观测值，会随着
CSST retry 写入而变化；契约和索引名称是稳定约定。

## 当前结论

- Warehouse 已不再依赖旧 `warehouse` Helm release。新运行时由本仓库的
  `atlas-warehouse` Helm release 自主管理 Elasticsearch、MinIO 和
  `ast_*` 索引初始化；Kafka 已改为可选依赖，当前 Scanner/Operator 不使用。
- 旧 `warehouse` namespace/release 已删除，旧的 5 个 PV 及其 NFS 数据目录
  已释放。冻结的 `/home/aaron/Repo/data-warehouse` 没有修改，也没有被作为
  运行时 fallback；`astro_*` 索引不在新链路中使用。
- Scanner/Operator 主链路已经完成修复和回归测试。CSST W1 catalog 全前缀
  retry 已在六小时 deadline 触发 `DeadlineExceeded`；对应 layer 仍为
  `UPDATING` 且 lease 已过期，必须先恢复为 `ACTIVE` 或 `FAILED`，Assets
  才能把该层视为 settled 数据。

## 已完成的 Warehouse 工作

### 扫描正确性与证据

- partial、item error、写入 error 或进程异常都会使 layer 保持 `FAILED`；只有
  真正完成且无 error 的扫描才会变为 `ACTIVE`。失败/partial coverage 即使
  物理上暂留在 Elasticsearch，也不会被查询返回。
- 失败 evidence 补齐了 phase、counts、source snapshot（如果已经生成）、
  inventory、normalized scan、errors 和 provenance，便于保留失败任务排查。
- FITS WCS 必须有可解析且显式的 ICRS celestial metadata；缺失或非 ICRS
  （例如 HI4PI 的 `FK5`）会失败并留下证据。
- `catalog-radec` 和 `catalog-healpix` 在读取行之前校验配置列；RA/Dec 或
  HEALPix 列缺失不会伪装成成功的空扫描。

### 资源与 IO

- layer lease 使用 execution id，支持 heartbeat、过期接管，并阻止未过期的
  重复执行。
- source enumeration、evidence 和 Elasticsearch bulk write 改为有界流式处理；
  bulk 按批次写入，避免一次性把大前缀或结果集放进内存。
- OSS/FITS header 使用 HTTP/S3 Range 读取，并保留受控 fallback；不会下载科学
  数组，也没有把原始天文文件变成 Warehouse proxy。

### Operator 与部署

- Operator 只负责校验 ScanPlan v2、投影 Secret 引用、创建/观察 Job 和报告
  status；科学解析和 Elasticsearch 写入仍在 Scanner 中。
- Scanner summary 必须可解析且完整，才会报告成功；Job 带 layer、survey、task
  tracking labels。
- Operator upgrade 或重复 reconcile 会按 request ownership、rendered plan
  hash 和 scanner image 复用等价 Job；非终态任务和成功任务优先于过期的失败
  duplicate。
- Job 新默认 termination grace period 为 120 秒。当前 Operator 镜像为
  `astro-atlas-operator:0.2.0-20260826-mocdiscovery`，未指定 image 的新请求
  默认使用 `astro-atlas-scanner:0.2.0-20260826-shutdownfix1`。
- MOC Discovery 已加入 namespaced `MocDiscoveryRequest` CRD。Assets 只提交
  `surveyName` 以及可选提示，Operator 在受控 CDS policy 下创建独立 evidence
  Job；当前实现镜像已推送为
  `astro-atlas-operator:0.2.0-20260826-mocdiscovery` 和
  `astro-atlas-moc-discovery:0.1.0-20260826`。Discovery 不写 `ast_*`，也不
  发布 CoverageLayer。CRD 已安装，Operator 已 rollout 1/1；Gaia/DR3
  `MocDiscoveryRequest` smoke 已成功，evidence 记录了 CDS HTTP 200 空响应、
  0 candidate/0 probe，不应把它解释成有命中的 discovery 结果。

### 公开巡天 MOC Discovery 与覆盖补充（2026-08-28）

在 Gaia smoke 之外又提交了四个后台 `MocDiscoveryRequest`，均已完成：

| 请求 | Job | 结果 | Evidence |
| --- | --- | --- | --- |
| `skymapper-moc-discovery-20260828` | `skymapper-moc-discovery-20260828-moc-discovery` | `SUCCEEDED`; CDS HTTP 200、0 bytes、0 candidate、0 probe | `moc-discovery/skymapper-moc-discovery-20260828-moc-discovery/execution-plan.json` |
| `kids-moc-discovery-20260828` | `kids-moc-discovery-20260828-moc-discovery` | `SUCCEEDED`; CDS HTTP 200、0 bytes、0 candidate、0 probe | `moc-discovery/kids-moc-discovery-20260828-moc-discovery/execution-plan.json` |
| `vista-viking-moc-discovery-20260828` | `vista-viking-moc-discovery-20260828-moc-discovery` | `SUCCEEDED`; CDS HTTP 200、0 bytes、0 candidate、0 probe | `moc-discovery/vista-viking-moc-discovery-20260828-moc-discovery/execution-plan.json` |
| `decals-moc-discovery-20260828` | `decals-moc-discovery-20260828-moc-discovery` | `SUCCEEDED`; CDS HTTP 200、0 bytes、0 candidate、0 probe | `moc-discovery/decals-moc-discovery-20260828-moc-discovery/execution-plan.json` |

这些 ObsCore 搜索的空响应只说明 discovery 端点没有返回候选，不否定已由
官方页面与 CDS record 复核的产品来源。四个 allowlisted CDS 空间 MOC 已保存
输入快照并通过离线 Core 重建，作为 Assets 的 `third_party_moc`、
`footprint_extent`、`estimated` 图层：

| Layer | 产品 | MOC 最大 order | order-4 cells |
| --- | --- | ---: | ---: |
| `skymapper-dr4-color-footprint` | SkyMapper DR4 g/r/i color | 10 | 2013 |
| `kids-dr5-color-footprint` | KiDS DR5 g/r/i color | 10 | 168 |
| `vista-viking-j-footprint` | VISTA VIKING J | 10 | 154 |
| `decals-dr5-color-footprint` | DECaLS DR5 g/r/z color | 10 | 1012 |

输入快照、record、MOC、query/preview/statistics/provenance 及可复现 recipe
位于 Assets 的 `artifacts/public-survey-footprints/` 和
`src/layers/recipes/`。这四项仍表示 CDS HiPS 的产品可用范围，不是接受 CCD
多边形、深度选择函数或光谱覆盖；STMOC 的时间轴只保留在 evidence 中。离线
公开清单目前为 90 products、38 acquired、47 manifest footprints、10 Core
layers；线上 Assets deployment 尚未因本次数据补充自动 reload。

## 稳定接口与索引

Warehouse 只维护以下三个当前状态索引：

```text
ast_layer_index_v1
ast_file_index_v1
ast_coverage_index_v1
```

`v1` 是 mapping/contract 版本，不是 scan run 版本。Assets 生产运行时应直接
使用配置的：

```text
ASSETS_WAREHOUSE_ES_URL=
  http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200
ASSETS_WAREHOUSE_LAYER_INDEX=ast_layer_index_v1
ASSETS_WAREHOUSE_COVERAGE_INDEX=ast_coverage_index_v1
ASSETS_WAREHOUSE_FILE_INDEX=ast_file_index_v1
```

读取约定：

- 只有 `ast_layer_index_v1.state=ACTIVE` 的 layer 可搜索。
- `UPDATING` 表示刷新尚未完成，`FAILED` 表示明确不可用；两者都不能在
  Assets 中表现成“空 coverage”。
- Coverage 保留真实的 ICRS、NESTED `order/ipix` 和 `exact`、`estimated`、
  `entrypoint-only` precision；不要把所有数据强制转换为 order 8，也不要从
  粗粒度预览推导更细 cell。
- FileAsset ID 是 canonical source URI 的稳定 hash。Coverage edge 按 layer
  替换，不能因为删除一个 Job 就删除 FileAsset 或其他 layer 的 edge。

## 当前集群状态

| 项目 | 当前状态 |
| --- | --- |
| 新基础设施 | Helm release `atlas-warehouse`，namespace `atlas-warehouse`，ES/MinIO 正常；Kafka 仅在显式启用时部署；ES 单节点 `green` |
| Operator | namespace `atlas-system`，Deployment `astro-atlas-operator`，1/1 ready |
| Assets | Helm release revision `83`，Deployment rollout revision `83`，Pod ready，NodePort `http://10.15.51.75:32083` |
| Assets public runtime | `/healthz` 返回 200；`/api/v1/coverage` 当前为 53 footprints，仍包含静态 public bundle |
| ES 观测快照 | 2026-08-28 某时点观测为 15 layer docs、22,844 FileAsset docs、92,487 physical coverage docs；CSST retry 正在替换 coverage，数字会变化且包含失败/partial 数据，不是 Assets 可发布计数 |

当前 CSST retry（已失败，待恢复）：

```text
ScanRequest: oss-csst-w1-catalog-full-bulkfix2-20260826
Job:         oss-csst-w1-catalog-full-bulkfix2-20260826-scan-f186ff54c3
layer:       csst-w1-phot-catalog
state:       UPDATING（lease 已过期）
phase:       DeadlineExceeded / 无最终 summary
image:       astro-atlas-scanner:0.2.0-20260826-bulkfix1
deadline:    6h
```

Job 在 2026-08-27 21:03 UTC 因六小时 active deadline 失败；其 layer 文档仍
保留 `UPDATING`、过期 lease、空 snapshot hash 和空最终计数。这个 Job、其
evidence 和失败 ScanRequest 必须保留，不能被 Assets 当作已完成结果使用。
下一步是用 `shutdownfix1` 或明确固定的新镜像提交 bounded retry，完成 layer
终态和 evidence 校验。`oss-csst-w1-catalog-full-retry3-20260828` 已于
2026-08-28 提交，当前在后台运行，使用 24 小时 deadline；不要原地修改该 Job。

其他关键 layer：

| layer | 状态 | 当前含义 |
| --- | --- | --- |
| `csst-sim-w1-image-extent` | `FAILED` | FITS spatial header 缺失；不可查询，失败证据保留 |
| `desi-merger-catalog` | `ACTIVE` | bounded catalog，2,039 coverage edges |
| `desi-overlap-catalog` | `ACTIVE` | bounded overlap catalog，5 coverage edges |
| `euclid-q1-vis-tile102018212` | `ACTIVE` | bounded VIS tile，4 files、44 coverage edges；两个 PSF FITS 因无空间 header 留在失败证据中 |
| Gaia/SDSS controlled layers | `ACTIVE` | Gaia 12 order-8 cells，SDSS 1 entrypoint-only cell |
| HI4PI controlled cube | `FAILED` | header 为非显式 ICRS，未伪造 coverage |

## Assets 侧如何理解任务和删除

Warehouse 的事实来源分两层：

1. **公共 coverage、reverse lookup 和 FileAsset 结果**：直接读取配置的新
   Elasticsearch endpoint 的 `ast_*` 当前索引，只使用 `ACTIVE` layer。
2. **任务运行状态和失败诊断**：读取 Kubernetes `ScanRequest`/Job status 及
   evidence。它们是操作记录，不是公共 coverage 的数据源。

因此，删除一个已经成功的 ScanRequest，Operator 会级联删除其拥有的 Job、Pod
和 plan ConfigMap，但不会删除 Elasticsearch 中的 `ast_*` 文档或 evidence。
公共 Assets coverage 不受影响；依赖 Kubernetes 资源展示“任务历史”的管理页面
会看不到该任务，所以成功任务可以按运维策略清理，失败任务建议保留。当前失败
任务和正在运行的 CSST retry 都必须保留。

## 已完成验证

- 根目录 `mvn test`：70 tests passed。
- `mvn package -DskipTests`：通过。
- Warehouse Helm lint/template、Kubernetes manifest dry-run、`git diff --check`：
  通过。
- 实际 probe 覆盖 HST multi-HDU、SDSS spectrum、Gaia RA/Dec、显式 HEALPix、
  HI4PI cube，以及 Euclid OSS bounded FITS；不支持或错误输入均产生显式结果。
- Assets 侧此前已通过 build/test；live smoke 已覆盖 health、manifest、
  coverage catalog/block、CSST-DESI overlap、DESI-Euclid overlap、overlap
  details、reverse lookup 和 FITS Range 读取。

## 当前遗留与共同验收

按以下顺序对齐：

1. 保留 `oss-csst-w1-catalog-full-bulkfix2-20260826` 的失败资源和 evidence，
   提交新的 bounded retry，检查 summary、snapshot hash、catalog row counts、
   errors 和最终 evidence。
2. 检查 `csst-w1-phot-catalog` 变为 `ACTIVE` 或明确 `FAILED`，并核对 layer
   `file_count`、`coverage_count` 与 evidence；不能留下过期 `UPDATING`。
3. 仅在成功后使用 Assets admin token 调用
   `POST /api/v1/admin/catalog/reload`，再检查
   `GET /api/v1/admin/catalog/status` 的 load mode、timestamp、数量和
   Warehouse connectivity。token 不写入本文件、计划、日志或 evidence。
4. 重跑 CSST/DESI/Euclid catalog、overlap、details、reverse lookup 和 Range
   smoke；确认 CSST retry 的新 coverage 出现在 Assets，旧失败 image layer 仍
   不可查询。

需要 Assets 侧另行处理的一项：当前 live Assets deployment 的
`ASSETS_WAREHOUSE_SCANNER_IMAGE` 已为 `astro-atlas-scanner:...-shutdownfix1`，
与 Warehouse Operator 默认值一致。CSST 旧 Job 仍固定为 `bulkfix1`，不要原地
修改；请通过新 ScanRequest retry 进行恢复。

## 参考文档

- [Warehouse handoff](../HANDOFF.md)
- [ScanPlan v2 contract](scan-plan.md)
- [Index contract](index-contract.md)
- [Operator contract](operator.md)
- [Project boundary](project-boundary.md)
- [Contract probe results](contract-probe-results-20260825.md)
- [ADR-0009 current layer and multi-order index](adr/0009-current-layer-multi-order-index.md)
- [ADR-0011 self-managed infrastructure](adr/0011-self-managed-warehouse-infrastructure.md)
