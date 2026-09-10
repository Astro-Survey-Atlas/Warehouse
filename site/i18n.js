/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(() => {
  "use strict";

  // Source-text keys leave markup, technical identifiers, and offline examples intact.
  const zh = {
    "Warehouse technical documentation: scan astronomical file metadata, maintain current sky coverage, and query explicit HEALPix cells.": "Warehouse 技术文档：扫描天文文件元数据，维护当前天空覆盖范围，并查询显式 HEALPix 像元。",
    "Skip to documentation": "跳转到文档",
    "Warehouse documentation home": "Warehouse 文档首页",
    "Docs": "文档",
    "Primary navigation": "主导航",
    "Quick start": "快速开始",
    "Index explorer": "索引浏览器",
    "GitHub repository": "GitHub 仓库",
    "Toggle theme": "切换主题",
    "On this page": "本页目录",
    "Documentation": "文档导航",
    "01 / Overview": "01 / 概览",
    "02 / Capabilities": "02 / 能力",
    "03 / Submit a scan": "03 / 提交扫描",
    "04 / Query coverage": "04 / 查询覆盖",
    "05 / Index explorer": "05 / 索引浏览器",
    "06 / Precision & state": "06 / 精度与状态",
    "Canonical contracts": "权威契约",
    "Index contract": "索引契约",
    "Astro Survey Atlas / Spatial directory": "Astro Survey Atlas / 空间目录",
    "Find the files.": "找到文件。",
    "Keep the sky in context.": "保留天空覆盖的上下文。",
    "Warehouse discovers astronomical files and maintains their current searchable sky coverage. Scan metadata, retain evidence, and resolve explicit HEALPix cells back to original source files. Scientific data stays at its source.": "Warehouse 发现天文文件，维护其当前可搜索的天空覆盖范围。扫描元数据、保留证据，并从显式 HEALPix 像元反查原始源文件。科学数据始终保留在源端。",
    "Submit your first scan": "提交首次扫描",
    "Explore a query response": "查看查询响应",
    "Scan data flow": "扫描数据流",
    "One finite intent": "一个有限的扫描意图",
    "CoverageLayer + source + mode": "CoverageLayer + 数据源 + 模式",
    "scan": "扫描",
    "FileAsset + SpatialCoverage + evidence": "FileAsset + SpatialCoverage + 证据",
    "lookup": "反查",
    "Current coverage": "当前覆盖范围",
    "ACTIVE layers in ast_* indices": "ast_* 索引中的 ACTIVE 图层",
    "Three projects, clear ownership": "三个项目，职责清晰",
    "Validates and executes scans, extracts file-level coverage, refreshes current indices, and retains inventory and extraction evidence. The Operator translates intent into namespaced Jobs.": "验证并执行扫描，提取文件级覆盖范围，刷新当前索引，保留清单与提取证据。Operator 将意图转换为命名空间内的 Job。",
    "Owns the public catalog, reviewed MOCs, releases, evidence presentation, overlap and reverse-lookup UX. Production reads the configured new Warehouse Elasticsearch directly.": "负责公共目录、经审核的 MOC、发布、证据展示、重叠分析和反查体验。生产环境直接读取所配置的新 Warehouse Elasticsearch。",
    "Owns user assets, connectors, labels, and local workflows. It can submit remote ScanRequests in an explicitly allowlisted namespace.": "负责用户资产、连接器、标签和本地工作流。可在明确列入允许名单的命名空间中提交远程 ScanRequest。",
    "Warehouse is not a workflow engine, universal catalog, reduction system, or download proxy. Evidence-only MOC discovery does not publish public geometry or write": "Warehouse 不是工作流引擎、通用目录、科学数据处理系统或下载代理。仅生成证据的 MOC 发现不会发布公共几何数据，也不会写入",
    "documents.": "文档。",
    "is reserved, not implemented.": "为保留概念，尚未实现。",
    "02 / Extraction": "02 / 提取",
    "Declare meaning, not processing steps": "声明语义，而非处理步骤",
    "A ScanPlan declares exactly one ExtractionMode for one source and one CoverageLayer. The scanner owns internal step ordering and validates the entire plan before enumeration or credentialed I/O.": "一个 ScanPlan 为一个数据源和一个 CoverageLayer 声明且仅声明一个 ExtractionMode。扫描器负责内部步骤顺序，并在枚举数据源或执行带凭据的 I/O 前验证整个计划。",
    "Image and cube footprints": "图像与数据立方体的覆盖轮廓",
    "Reads supported WCS headers and rasterizes at the required": "读取支持的 WCS 头信息，并按必需的",
    ". Sampled coverage is": "进行栅格化。采样覆盖的精度为",
    "; there is no silent center-point fallback.": "；不会静默退回中心点。",
    "Header position evidence": "头信息位置证据",
    "Maps an explicit FITS header position at the required": "按必需的",
    ". Precision is": "映射显式 FITS 头信息位置。精度为",
    ", not an instrument footprint.": "，而非仪器覆盖轮廓。",
    "Catalog occupancy": "星表占用范围",
    "Reads configured RA and Dec columns in ICRS degrees and maps them to NESTED cells at": "读取以 ICRS 度数表示的已配置 RA 和 Dec 列，并映射到以下阶数的 NESTED 像元：",
    ". Coverage describes file occupancy, not a per-row object index.": "。覆盖描述文件占用范围，而非逐行对象索引。",
    "Explicit source cells": "显式源像元",
    "Requires a pixel column and exactly one fixed source order or order column. Preserves explicit NESTED order/ipix; an absent source order is never assumed to be 8.": "需要像元列，并在固定源阶数与阶数列中二选一。保留显式 NESTED order/ipix；绝不将缺失的源阶数假定为 8。",
    "Local, S3-compatible, and OSS sources": "本地、S3 兼容与 OSS 数据源",
    "The": "使用",
    "connector accepts a directory or single file. S3-compatible and OSS endpoints use the AWS SDK adapter, with canonical": "连接器可读取目录或单个文件。S3 兼容与 OSS 端点使用 AWS SDK 适配器，规范标识为",
    "or": "或",
    "identities. Supply a signing region when required. Credential references point to environment variables or mounted files; never put credential values in a plan.": "。需要时请提供签名区域。凭据引用指向环境变量或挂载文件；切勿将凭据值放入计划。",
    "Metadata only.": "仅读取元数据。",
    "FITS extraction reads headers, and catalogs expose configured spatial columns. Warehouse never reads or copies scientific image, spectrum, or cube arrays. Unsupported formats remain discovered FileAssets with evidence and no invented coverage.": "FITS 提取仅读取头信息，星表仅读取配置的空间列。Warehouse 从不读取或复制科学图像、光谱或数据立方体数组。不支持的格式仍保留为已发现的 FileAsset 并附有证据，不会虚构覆盖范围。",
    "Declare modality explicitly:": "显式声明 modality：",
    ", or": "，或",
    "03 / Submission": "03 / 提交",
    "From local diagnostic to persisted scan": "从本地诊断到持久化扫描",
    "Start with a bounded catalog and a complete ScanPlan v2. The example below expects CSV or TSV files with": "从一个范围有限的星表和完整的 ScanPlan v2 开始。以下示例要求 CSV 或 TSV 文件包含",
    "and": "和",
    "columns under": "列，文件位于",
    "1. Inspect a local catalog": "1. 检查本地星表",
    "This is a complete": "这是一份完整的",
    ". Adapt the source and evidence paths to your machine. The sink remains declared even when": "。请根据本机环境调整数据源与证据路径。即使",
    "bypasses Elasticsearch.": "绕过 Elasticsearch，仍需声明 sink。",
    "Copy plan": "复制计划",
    "Local CLI / from the repository root": "本地 CLI / 在仓库根目录执行",
    "Copy CLI": "复制 CLI",
    "Memory diagnostics do not persist searchable state.": "内存诊断不会持久化可搜索状态。",
    "Evidence is optional only with": "仅在使用",
    "; if declared, use a writable output path. To persist, omit": "时证据可选；若声明证据，请使用可写的输出路径。要持久化，请去掉",
    ", configure the new Warehouse Elasticsearch endpoint and credential references, provision the strict indices, and retain evidence. Scanner startup does not create or recreate indices.": "，配置新 Warehouse Elasticsearch 端点及凭据引用，预先创建严格映射索引，并保留证据。扫描器启动时不会创建或重建索引。",
    "2. Submit a namespaced ScanRequest": "2. 提交命名空间内的 ScanRequest",
    "This complete request wraps the same local catalog intent for Kubernetes. Install infrastructure and the Operator with the supported Helm charts first. Provision the evidence PVC and a Bound source PVC labelled": "这份完整请求将相同的本地星表意图封装为 Kubernetes 请求。请先用受支持的 Helm chart 安装基础设施和 Operator。在以下命名空间内预先创建证据 PVC，以及处于 Bound 状态且带有标签",
    "in": "的数据源 PVC，命名空间为",
    ". The source claim's": "。数据源 PVC 的",
    "subdirectory is mounted read-only at": "子目录以只读方式挂载到",
    "Copy request": "复制请求",
    "The endpoint and image are example deployment settings, not universal defaults. This request assumes a sink without authentication; authenticated deployments must add namespace-local Secret bindings and plan credential references. Source paths must stay inside the declared source mount, and evidence paths inside the evidence mount. Host paths and cross-namespace claims are not supported.": "端点与镜像仅为部署示例，并非通用默认值。此请求假定 sink 无需认证；需要认证的部署必须添加命名空间内的 Secret 绑定及计划凭据引用。数据源路径必须位于声明的源挂载内，证据路径必须位于证据挂载内。不支持主机路径与跨命名空间的 PVC 引用。",
    "Upstream examples:": "上游示例：",
    "complete local request": "完整本地请求",
    "OSS request with Secret references": "带 Secret 引用的 OSS 请求",
    ", and": "，以及",
    "Helm installation": "Helm 安装",
    "3. Observe execution and evidence": "3. 查看执行状态与证据",
    "Submit and monitor / configured cluster required": "提交与监控 / 需要已配置的集群",
    "Copy commands": "复制命令",
    "Wait for a Job name before requesting logs. Request phases are": "请等待 Job 名称出现后再请求日志。请求阶段包括",
    ". The summary reports counts, available orders, source snapshot hash, errors, and evidence path. Detailed inventory, normalized scan, provenance, and errors stay on the evidence volume, not in the browser's initial request.": "。摘要报告计数、可用阶数、源快照哈希、错误与证据路径。详细清单、规范化扫描、来源记录与错误保留在证据卷上，不会包含在浏览器初始请求中。",
    "The Operator creates or adopts an immutable plan ConfigMap and execution-hash-named Job. All resources and credential references remain in the request namespace. The default allowlist watches only": "Operator 创建或接管不可变的计划 ConfigMap 和以执行哈希命名的 Job。所有资源与凭据引用均保留在请求命名空间内。默认允许名单仅监视",
    "; Workspace submissions in": "；Workspace 在",
    "require explicit opt-in. An empty allowlist fails closed.": "中的提交需要显式启用。允许名单为空时拒绝运行。",
    "04 / Reverse lookup": "04 / 反向查询",
    "Ask for cells. Receive file candidates.": "查询像元，获取候选文件。",
    "The Query API is read-only and diagnostic, not a scan submission endpoint or a required production hop. Assets performs this lookup directly against its configured Warehouse Elasticsearch.": "Query API 是只读诊断接口，不是扫描提交端点，也不是生产环境的必经环节。Assets 直接对其配置的 Warehouse Elasticsearch 执行此查询。",
    "Copy curl": "复制 curl",
    "Replace pixel 123 with a cell from your scan evidence. Supply comma-separated": "请将像元 123 替换为扫描证据中的像元。提供逗号分隔的",
    ", and one explicit": "，以及一个显式的",
    ". Every requested layer must be ACTIVE and support that order. Results contain unique files and their": "。每个请求图层都必须处于 ACTIVE 状态并支持该阶数。结果包含去重后的文件及其",
    "; they are cell-intersection candidates, not exact geometric overlap claims.": "；它们是像元相交候选，不代表精确的几何重叠。",
    "The default limit is 100, maximum 1000. A response or edge limit sets": "默认限制为 100，最大为 1000。达到响应或关联边限制时会设置",
    ". When": "。当",
    "is non-null, pass it as the opaque": "非 null 时，将其作为不透明的",
    "with the same normalized layers, order, and pixels. Do not treat truncation as a precision value or assume every truncated response has another page.": "传入，并保持规范化后的图层、阶数和像元不变。不要将截断视为精度值，也不要假定每个截断响应都有下一页。",
    "Offline response fixtures": "离线响应示例",
    "These bundled examples illustrate contract behavior. They do not contact a live service, query your indices, or report current survey availability.": "这些内置示例展示契约行为，不会连接在线服务、查询你的索引或报告当前巡天数据的可用性。",
    "Response scenario": "响应场景",
    "Exact catalog occupancy": "精确星表占用范围",
    "Estimated WCS footprint": "估计的 WCS 覆盖轮廓",
    "UPDATING layer unavailable": "UPDATING 图层不可用",
    "FAILED layer unavailable": "FAILED 图层不可用",
    "Truncated candidate response": "截断的候选响应",
    "Offline fixture viewer. JavaScript loads the selected example.": "离线示例查看器。JavaScript 将加载所选示例。",
    "Fixture response / not live data": "示例响应 / 非实时数据",
    "Copy response": "复制响应",
    "Offline query response": "离线查询响应",
    "05 / Storage contract": "05 / 存储契约",
    "Three indices. One current state.": "三个索引，一份当前状态。",
    "Strict mappings separate layer lifecycle, global file identity, and layer-scoped spatial associations. The": "严格映射将图层生命周期、全局文件标识与图层内空间关联分离。",
    "suffix versions mappings, not scan runs. Legacy": "后缀表示映射版本，而非扫描运行。旧的",
    "indices are untouched and never used as fallback.": "索引不受影响，也绝不作为回退。",
    "holds survey/release/product identity, modality, role, entrypoint, state, lease, snapshot hash, available orders, counts, and errors. Document ID:": "保存巡天/发布/产品标识、modality、角色、入口、状态、租约、快照哈希、可用阶数、计数和错误。文档 ID：",
    "holds canonical URI, name/type, parent URI, size, and timestamps. Document ID: SHA-256 of the canonical source URI. No raw data, modality, or layer-owned coverage list.": "保存规范 URI、名称/类型、父 URI、大小和时间戳。文档 ID 是规范源 URI 的 SHA-256。不保存原始数据、modality 或图层所属的覆盖列表。",
    "associates a layer and file with an explicit cell, method, role, modality, and precision. Identity is deterministic over layer, file, order, cell, and role.": "将图层与文件关联到显式像元、方法、角色、modality 和精度。标识由图层、文件、阶数、像元和角色确定性生成。",
    "Choose an index schema": "选择索引结构",
    "Layer": "图层",
    "File": "文件",
    "Coverage": "覆盖",
    "The offline explorer displays mapping fields and illustrative documents. Read the canonical templates for the authoritative field definitions.": "离线浏览器展示映射字段与示例文档。权威字段定义请参阅规范模板。",
    "Canonical sources:": "权威来源：",
    "strict Elasticsearch templates": "严格 Elasticsearch 模板",
    "index identity, write, and read contract": "索引标识、写入与读取契约",
    "06 / Invariants": "06 / 不变量",
    "Precision is not completeness": "精度不等于完整性",
    "Coverage always retains its actual ICRS, NESTED HEALPix": "覆盖始终保留实际的 ICRS、NESTED HEALPix",
    ". Coarsening finer data is allowed; expanding coarse coverage into claimed finer precision is not.": "。允许将更细的数据降阶；不允许扩展粗粒度覆盖并声称具有更高精度。",
    "Exact cell assignment for the represented input, such as catalog occupancy. It does not turn a cell intersection into an exact geometry claim.": "对所表示的输入进行精确像元分配，例如星表占用范围。这不意味着像元相交就是精确几何重叠。",
    "Approximate coverage, including sampled WCS rasterization. Preserve that qualification in reverse-lookup results.": "近似覆盖，包括采样 WCS 栅格化。反查结果必须保留这一精度限定。",
    "Limited positional or entrypoint evidence, not a full footprint. If only an official URL is known, Assets reads the layer entrypoint; no synthetic file or coverage edge is created.": "有限的位置或入口证据，而非完整覆盖轮廓。如果仅知道官方 URL，Assets 读取图层入口；不会创建虚构文件或覆盖关联边。",
    "Refresh a layer, not a historical index generation": "刷新图层，而非生成历史索引版本",
    "An expiring lease prevents overlapping refreshes. Old layer coverage is deleted before replacement; the layer is unavailable during the update, never silently empty.": "有期限的租约防止并发刷新。替换前删除旧图层覆盖；更新期间图层不可用，绝不会静默返回空结果。",
    "Only verified successful current state is searchable. A successful empty scan is ACTIVE with zero coverage, a valid empty result.": "只有经过验证且成功的当前状态可搜索。成功的空扫描处于 ACTIVE 状态、覆盖数为零，是有效的空结果。",
    "Partial or failed work is unavailable. Any physical partial edges remain hidden behind the state gate; old coverage is not served as a fallback.": "部分完成或失败的结果不可用。任何已写入的部分关联边都被状态检查隐藏；不会回退提供旧覆盖。",
    "Failure is terminal for an execution.": "一次执行遇到失败即终止。",
    "The first extraction error, including a malformed catalog row, stops the scan and is retained as evidence. Later input is not enumerated. Jobs use": "首个提取错误（包括格式错误的星表行）会停止扫描并保留为证据。后续输入不再枚举。Job 使用",
    "; diagnose and submit a new request/execution identity to retry. Only bounded transport retries remain. Suffix filtering, blank lines, and comments are not failures.": "；重试前请诊断问题，并提交新的请求/执行标识。仅保留有界的传输层重试。后缀过滤、空行和注释不是失败。",
    "A SourceSnapshot retains the named inventory and SHA-256 identity with enumeration and extraction errors. Evidence lives outside the online indices and browser startup data. Job TTL is operational cleanup, not public scan history. Credentials never belong in plan values, evidence documents, logs, indices, or query responses.": "SourceSnapshot 保留具名清单、SHA-256 标识以及枚举和提取错误。证据不属于在线索引或浏览器启动数据。Job TTL 用于运维清理，而非公共扫描历史。凭据绝不能出现在计划值、证据文档、日志、索引或查询响应中。",
    "Further reading:": "延伸阅读：",
    "architecture": "架构",
    "project boundaries": "项目边界",
    "deployment self-test": "部署自检",
    "Static documentation. Offline examples. No live service connection.": "静态文档。离线示例。不连接在线服务。",
    "Source & canonical contracts on GitHub": "GitHub 上的源码与权威契约",
    "Current state of one survey, release, and product. Only ACTIVE layers are queryable; this document is not a MOC or a coverage-cell list.": "一个巡天、发布和产品的当前状态。仅 ACTIVE 图层可查询；此文档不是 MOC 或覆盖像元列表。",
    "Stable CoverageLayer identity and Elasticsearch document ID.": "稳定的 CoverageLayer 标识及 Elasticsearch 文档 ID。",
    "Survey identity for this layer.": "此图层的巡天标识。",
    "Survey release identity, not an index mapping version or scan run.": "巡天发布标识，而非索引映射版本或扫描运行。",
    "Product identity within the survey release.": "巡天发布内的产品标识。",
    "Declared data kind: image, spectrum, cube, catalog, timeseries, visibility, event, or other.": "声明的数据类型：image、spectrum、cube、catalog、timeseries、visibility、event 或 other。",
    "Spatial meaning of the association: footprint or occupancy.": "关联的空间语义：footprint 或 occupancy。",
    "Optional official product/download URL. Assets may label this entrypoint-only when file-level reverse mapping is unavailable; the API does not manufacture an edge.": "可选的官方产品/下载 URL。文件级反查不可用时，Assets 可将其标注为 entrypoint-only；API 不会虚构关联边。",
    "ACTIVE, UPDATING, or FAILED. UPDATING and FAILED are explicit query errors, never empty coverage results.": "ACTIVE、UPDATING 或 FAILED。UPDATING 和 FAILED 返回明确的查询错误，绝不是空覆盖结果。",
    "Execution identity for the current refresh, not a historical index generation.": "当前刷新的执行标识，而非历史索引版本。",
    "Expiring refresh lease required for UPDATING; null for ACTIVE or FAILED.": "UPDATING 必须具有有期限的刷新租约；ACTIVE 或 FAILED 时为 null。",
    "SHA-256 of the consumed source inventory, not a raw scientific-file checksum. Full inventory and errors remain in external evidence. This sample digest is illustrative.": "已读取源清单的 SHA-256，而非原始科学文件的校验和。完整清单与错误保留在外部证据中。此摘要仅为示例。",
    "Array of available explicit NESTED HEALPix orders. Elasticsearch maps each value as integer, not as a separate array type. Coarse data cannot satisfy finer-order queries.": "可用的显式 NESTED HEALPix 阶数数组。Elasticsearch 将每个值映射为 integer，而非单独的数组类型。粗粒度数据不能满足更高阶查询。",
    "Nonnegative current-layer file count. FileAsset identities are global, not layer-owned.": "当前图层的非负文件数。FileAsset 标识是全局的，不属于单个图层。",
    "Nonnegative count of current SpatialCoverage edges, not unique pixels or catalog rows.": "当前 SpatialCoverage 关联边的非负数量，而非唯一像元数或星表行数。",
    "Nonnegative number of recorded scan/extraction errors; details are retained as evidence.": "记录的扫描/提取错误的非负数量；详情保留为证据。",
    "Credential-free failure summary; null when no failure summary is present.": "不含凭据的失败摘要；没有失败摘要时为 null。",
    "Timestamp of the current layer-state update.": "当前图层状态更新的时间戳。",
    "Global identity and source metadata for one discovered file. No raw payload, modality, extraction mode, layer ID, or coverage list is stored here.": "一个已发现文件的全局标识和源元数据。不在此存储原始数据、modality、提取模式、图层 ID 或覆盖列表。",
    "SHA-256 of the canonical source URI; also the document ID. Not a content checksum and not derived from a layer ID.": "规范源 URI 的 SHA-256，同时也是文档 ID。不是内容校验和，也不由图层 ID 派生。",
    "Canonical source URI of the discovered file, without credential values. Warehouse does not proxy downloads.": "已发现文件的规范源 URI，不含凭据值。Warehouse 不代理下载。",
    "Name of the discovered source file.": "已发现源文件的名称。",
    "Parent location of the canonical source URI.": "规范源 URI 的父级位置。",
    "Detected file type, serialized using the FileType enum name (for example FITS). Not an ExtractionMode.": "检测到的文件类型，使用 FileType 枚举名称（如 FITS）序列化。不是 ExtractionMode。",
    "Source file size in bytes, nullable when unknown.": "源文件大小，单位为字节；未知时可为 null。",
    "Source last-modified timestamp, nullable when unknown.": "源文件最后修改时间戳；未知时可为 null。",
    "Latest FileAsset indexing timestamp.": "最近一次 FileAsset 索引写入时间戳。",
    "One layer-to-file association at one explicit ICRS, NESTED HEALPix cell. It is not an inverted array of file IDs or an exact geometry guarantee.": "在一个显式 ICRS、NESTED HEALPix 像元上的图层与文件关联。它不是文件 ID 的倒排数组，也不保证精确几何关系。",
    "CoverageLayer identity; the layer must be ACTIVE before this edge can participate in a query.": "CoverageLayer 标识；图层必须处于 ACTIVE 状态，此关联边才能参与查询。",
    "Associated global FileAsset ID, derived from its canonical source URI.": "关联的全局 FileAsset ID，由其规范源 URI 派生。",
    "Canonical source URI for the associated discovered file, without credential values.": "关联的已发现文件的规范源 URI，不含凭据值。",
    "Actual stored HEALPix order. Reverse lookup matches this explicit order; coarse cells are never expanded into invented finer coverage.": "实际存储的 HEALPix 阶数。反查匹配此显式阶数；绝不将粗像元扩展为虚构的更细覆盖。",
    "NESTED pixel index (ipix) at healpix_order; valid range is 0 through 12 * 4^order - 1.": "healpix_order 阶的 NESTED 像元索引（ipix）；有效范围为 0 到 12 * 4^order - 1。",
    "Explicit celestial reference frame: ICRS.": "显式天球参考系：ICRS。",
    "Explicit HEALPix numbering scheme: NESTED.": "显式 HEALPix 编号方案：NESTED。",
    "Extraction method: fits_wcs, fits_header_position, catalog_radec, or catalog_healpix. Indexed values use underscores, unlike ScanPlan mode values.": "提取方法：fits_wcs、fits_header_position、catalog_radec 或 catalog_healpix。索引值使用下划线，与 ScanPlan 模式值不同。",
    "Association role: footprint or occupancy. This role participates in edge identity.": "关联角色：footprint 或 occupancy。此角色参与关联边标识的生成。",
    "Declared layer modality copied onto the association, not inferred from the FileAsset.": "复制到关联上的已声明图层 modality，而非从 FileAsset 推断。",
    "Coverage precision: exact, estimated, or entrypoint-only. Response truncation is separate; exact cell evidence is not exact polygon refinement.": "覆盖精度：exact、estimated 或 entrypoint-only。响应截断是独立属性；精确像元证据不等于精确多边形细化。",
    "Optional original source HEALPix order, retained when applicable; null when no source order is supplied. Finer inputs may be coarsened, never the reverse.": "可选的原始源 HEALPix 阶数，适用时保留；未提供时为 null。更细的输入可以降阶，反之则不允许。",
    "Strict mapping: unknown fields are rejected. All keyword fields use ignore_above: 2048. The v1 suffix versions mappings, not scan runs. Select a field to inspect its example.": "严格映射：拒绝未知字段。所有 keyword 字段使用 ignore_above: 2048。v1 后缀表示映射版本，而非扫描运行。选择字段查看示例。",
    "Field": "字段",
    "Mapped type": "映射类型",
    "Description": "说明",
    "Illustrative document (not live data)": "示例文档（非实时数据）",
    "Copy JSON": "复制 JSON",
    "Choose an offline fixture. No request is sent.": "请选择离线示例。不会发送请求。",
    "HTTP 409 example: UPDATING is unavailable, not empty coverage.": "HTTP 409 示例：UPDATING 表示不可用，而非空覆盖。",
    "HTTP 409 example: FAILED is unavailable, not empty coverage.": "HTTP 409 示例：FAILED 表示不可用，而非空覆盖。",
    "HTTP 200 example: limited response, truncated=true; precision remains exact. The opaque cursor shown is a non-executable placeholder.": "HTTP 200 示例：响应受限，truncated=true；精度仍为 exact。显示的不透明游标是不可执行的占位符。",
    "HTTP 200 example: estimated WCS candidate, not an exact geometry claim; response is not truncated.": "HTTP 200 示例：estimated WCS 候选，不代表精确几何关系；响应未截断。",
    "HTTP 200 example: exact cell occupancy at order 6, pixel 1024; response is not truncated. This is a candidate lookup, not polygon refinement.": "HTTP 200 示例：阶数 6、像元 1024 上的 exact 像元占用；响应未截断。这是候选反查，而非多边形细化。",
    "Offline fixture only. No network request or live execution.": "仅为离线示例。无网络请求或在线执行。",
    "Copy unavailable: the target element was not found.": "无法复制：未找到目标元素。",
    "Copied to clipboard.": "已复制到剪贴板。",
    "Clipboard unavailable or permission denied. Select the displayed text and copy it manually.": "剪贴板不可用或权限被拒绝。请选择显示的文本并手动复制。"
  };

  const storageKey = "warehouse-language";
  let language = "zh";
  try {
    const stored = localStorage.getItem(storageKey);
    if (stored === "en" || stored === "zh") language = stored;
  } catch { /* Storage can be unavailable on file:// or in private browsing. */ }

  const originals = new WeakMap();
  const attributes = new WeakMap();
  const normalize = text => text.trim().replace(/\s+/g, " ");
  const t = text => language === "zh" ? (zh[normalize(text)] ?? text) : text;

  function translate(root = document.body) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const node = walker.currentNode;
      if (node.parentElement.closest("pre, code, script, style, noscript, #language-toggle, #theme-toggle")) continue;
      const previous = originals.get(node);
      const source = previous && node.data === previous.rendered ? previous.source : node.data;
      const key = normalize(source);
      if (!Object.hasOwn(zh, key)) continue;
      const rendered = source.replace(/\S[\s\S]*\S|\S/, t(key));
      if (node.data !== rendered) node.data = rendered;
      originals.set(node, { source, rendered });
    }
    root.querySelectorAll("[aria-label], [title], meta[name=description]").forEach(element => {
      if (element.id === "language-toggle" || element.id === "theme-toggle") return;
      const saved = attributes.get(element) ?? {};
      for (const name of ["aria-label", "title", "content"]) {
        if (!element.hasAttribute(name)) continue;
        const current = element.getAttribute(name);
        const source = saved[name]?.rendered === current ? saved[name].source : current;
        const rendered = t(source);
        element.setAttribute(name, rendered);
        saved[name] = { source, rendered };
      }
      attributes.set(element, saved);
    });
  }

  function applyLanguage() {
    document.documentElement.lang = language === "zh" ? "zh-CN" : "en";
    translate(document.documentElement);
    const button = document.getElementById("language-toggle");
    button.hidden = false;
    button.textContent = language === "zh" ? "English" : "中文";
    button.lang = language === "zh" ? "en" : "zh-CN";
    button.setAttribute("aria-label", language === "zh" ? "Switch to English" : "切换为中文");
    document.dispatchEvent(new Event("warehouse-language-change"));
  }

  window.WarehouseI18n = { t, translate };
  document.getElementById("language-toggle").addEventListener("click", () => {
    language = language === "zh" ? "en" : "zh";
    try { localStorage.setItem(storageKey, language); } catch { /* Switching still works without persistence. */ }
    applyLanguage();
  });
  applyLanguage();
})();
