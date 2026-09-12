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
    "Warehouse technical documentation: scan local, S3-compatible, and OSS astronomical sources into Elasticsearch file and sky-coverage indices.": "Warehouse 技术文档：把本地、S3 兼容和 OSS 天文数据源扫描写入 Elasticsearch 的文件与天空覆盖索引。",
    "Related documentation": "相关文档",
    "Astro Survey Atlas / Scan engine": "Astro Survey Atlas / 扫描引擎",
    "Scan your data sources.": "扫描你的数据源。",
    "Build a searchable sky index.": "建成可查询的天区索引。",
    "Warehouse is a scanning tool for astronomical data. Point it at a local directory, an S3-compatible bucket, or OSS storage. It inventories every file it finds, extracts each file's sky coverage from supported metadata, and writes the current results into Elasticsearch ast_* indices, retaining evidence for every scan. It never reads or copies scientific arrays.": "Warehouse 是ASA项目开发的一个天文数据扫描工具。可以把本地目录、S3 兼容存储或 OSS 交给它：它会清点发现的每一个文件，从文件的元数据中提取每个文件对应的天空覆盖，并把当前结果写入 Elasticsearch 的 ast_* 索引，每次扫描都会保留证据。它从不读取或复制科学数据。",
    "Once a scan lands in the index, the sky-position questions come from your application: which files cover this region, where do they live, and why did they match. Assets asks this in production; your code can query the same indices.": "扫描结果写入索引后，按天区反查等问题就能被回答，例如有哪些文件覆盖该区域、它们存放在哪里、为什么被命中? ",
    "See what lands in the index": "看看扫描写入了什么",
    "Your data source": "你的数据源",
    "local / S3-compatible / OSS": "本地 / S3 兼容 / OSS",
    "file inventory + sky coverage + evidence": "文件清单 + 天空覆盖 + 证据",
    "write": "写入ES",
    "ast_* indices": "ast_* 索引",
    "current state in Elasticsearch": "Elasticsearch 中的当前状态",
    "query": "按天区反查",
    "Your application": "你的应用",
    "Assets, or your own code": "Assets，或你自己的代码",
    "What Warehouse does with your data": "Warehouse 对你的数据做了什么",
    "Inventory the files": "清点文件",
    "One scan walks one source and records every discovered file as a FileAsset with its canonical URI, name, size, and timestamps.": "一次扫描遍历一个数据源，把发现的每个文件记录为 FileAsset，包含规范 URI、名称、大小和时间戳。",
    "Extract sky coverage from metadata": "从元数据提取天空覆盖",
    "One extraction mode per scan reads WCS headers, catalog RA/Dec columns, or explicit HEALPix cells. Every result carries its precision: exact, estimated, or entrypoint-only.": "每次扫描用一种提取模式读取 WCS 头、星表 RA/Dec 列或已有的 HEALPix 像元。每条结果都带有精度标记：exact、estimated 或 entrypoint-only。",
    "Publish current state to Elasticsearch": "把当前状态写入 Elasticsearch",
    "Each scan refreshes one CoverageLayer in the ast_* indices. Only ACTIVE layers are queryable; failed scans stay visible with errors and evidence instead of serving stale coverage.": "每次扫描刷新 ast_* 索引中的一个 CoverageLayer。只有 ACTIVE 图层可查询；失败的扫描会保留错误和证据，而不是继续提供过期覆盖。",
    "A CoverageLayer groups one survey, release, and product; one scan covers one source, one layer, and one extraction mode. To answer \"which files cover this part of the sky\", query the indices a scan produced: Assets reads them in production, and the diagnostic Query API lets you try the same lookup.": "CoverageLayer（覆盖图层）对应一个巡天的某个发布版本和数据产品；一次扫描只处理一个数据源、一个图层和一种提取模式。要回答“这片天区有哪些文件”，就去查询扫描产出的索引：姊妹项目 Assets 可以直接读取它们，你也可以用诊断 Query API 执行同样的查询。",
    "Databases (JDBC) are planned, not supported yet.": "数据库（JDBC）为规划能力，暂不支持。",
    "The current ScanPlan accepts local, S3-compatible, and OSS sources only. Until a JDBC connector lands, scan catalog databases with Workspace, whose connectors include JDBC and write to its own index.": "当前 ScanPlan 仅接受本地、S3 兼容和 OSS 数据源。在 JDBC 连接器落地之前，请用 Workspace 扫描星表数据库——它的连接器包含 JDBC，并写入自己的索引。",
    "Which files contain data for this part of the sky? Warehouse helps your application answer that question. Give it a file source to scan, then query sky cells to get matching files, their original locations, and the coverage behind each match.": "这片天区有哪些数据文件？Warehouse 帮你的应用回答这个问题。指定数据源并完成扫描后，就能按天区像元查找候选文件，拿到原始文件地址，以及每个文件被选中的覆盖依据。",
    "Choose your data": "指定数据源",
    "What you can build with Warehouse": "把按天区找文件的能力接入你的应用",
    "Make files searchable by sky position": "让文件可以按天区检索",
    "Scan a local directory or object-storage source. Warehouse records the files it finds and extracts their sky coverage from supported metadata.": "接入本地目录或对象存储，Warehouse 会记录发现的文件，并从支持的元数据中提取它们的天空覆盖范围。",
    "Find candidate files for a region": "找到目标天区的候选文件",
    "Choose the survey layers and HEALPix cells you want to search. Get a deduplicated file list with source URIs and the cells that matched.": "选好要查询的巡天图层和 HEALPix 像元，即可得到去重后的文件列表，其中包含源文件 URI 和命中的像元。",
    "Explain each match": "知道文件为什么被选中",
    "Use the coverage method and precision to show why a file was returned. Scan summaries and detailed evidence help you investigate missing coverage or failed scans.": "通过提取方法和精度标记，向用户说明匹配依据。遇到覆盖缺失或扫描失败时，还可以用扫描摘要和详细记录排查原因。",
    "A CoverageLayer groups one survey, release, and product for scanning and querying. Your application chooses the layers to search; Warehouse returns file metadata and coverage. To open or download a file, use its original source location with the access your application already has.": "CoverageLayer（覆盖图层）对应一个巡天的某个发布版本和数据产品，是扫描和查询的基本单位。你的应用选择图层，Warehouse 返回文件元数据及覆盖信息。需要打开或下载文件时，再按原始地址访问，并使用数据源要求的访问权限。",
    "Choose how to read your sky coverage": "四种扫描模式",
    "What if one folder contains multiple kinds of files?": "如果一个文件夹下有多种不同的文件呢？",
    "Astronomical data is usually separated by product type into directories or object prefixes. Create a ScanPlan for one directory or prefix and choose the extraction mode that matches that batch of files. You can filter by suffixes and exclude README/checksum files or other auxiliary inputs. Only when one path mixes different spatial-recording methods do you need to split into multiple scan plans.": "天文数据通常已经按产品类型存放在不同目录或对象前缀中。为一个目录或前缀创建 ScanPlan，并选择与这批文件一致的提取模式。可以使用文件后缀和排除规则过滤 README、校验文件等辅助内容。只有当同一路径混放了不同空间记录方式的数据时，才需要拆成多个扫描计划。",
    "Run a scan, then check the result": "提交扫描，查看结果",
    "Connecting to an existing deployment? Ask its operator for your request namespace, source and evidence volumes, scanner image, and Elasticsearch settings. Submit scans through Kubernetes ScanRequest. The read-only Query API is used later to look up files.": "接入已有的 Warehouse 服务时，请先向服务管理员获取可用的命名空间、数据源卷、扫描记录卷、扫描器镜像和 Elasticsearch 配置。您的扫描任务需要通过 Kubernetes CRD ScanRequest 提交；后面的只读 Query API 用于查询文件。",
    "For a deployed Warehouse, submit the plan as a ScanRequest using the example below. Ask your operator to provide an evidence PVC and a Bound source PVC labelled": "在已部署的 Warehouse 上，可以按下面的示例用 ScanRequest 提交计划。请让管理员准备好扫描记录 PVC，以及处于 Bound 状态、带有以下标签的数据源 PVC：",
    "Submission is asynchronous: accepting the request does not mean the scan has finished. Follow its status until SUCCEEDED, FAILED, or INVALID. If it remains WAITING, inspect the status message. Your namespace must be enabled by the deployment operator; all volume and Secret references belong in that same namespace.": "扫描异步执行：请求被接受，并不代表扫描已经完成。请持续查看状态，直到 SUCCEEDED、FAILED 或 INVALID。如果一直处于 WAITING，先检查状态消息。提交前需由管理员启用你的命名空间，引用的存储卷和 Secret 也必须位于该命名空间。",
    "Already have an indexed layer? Send its ID and the sky cells you want to search to the diagnostic Query API below. Ask your operator for its address if it is enabled. Applications can also read Warehouse Elasticsearch directly; this is how Assets performs production lookups.": "已有完成索引的图层？把图层 ID 和目标天区像元传给下面的诊断 Query API 即可查询。如果部署启用了这个接口，请向管理员获取地址。应用也可以直接查询 Warehouse Elasticsearch；Assets 的生产查询就采用这种方式。",
    "Read the response in three steps": "拿到返回结果后，看这三处",
    "Each item contains fileId, sourceUri, fileName, and fileType. Use sourceUri to locate the original file; Warehouse does not serve its contents.": "每个 item 包含 fileId、sourceUri、fileName 和 fileType。用 sourceUri 定位原始文件；文件内容需要到数据源获取。",
    "Check why they matched.": "为什么命中？",
    "matchingCoverage lists the matching layerId, order, pixel, method, role, and precision. One file can have several matching cells.": "matchingCoverage 列出命中的 layerId、order、pixel、method、role 和 precision。同一个文件可能命中多个像元。",
    "Check whether there is more.": "结果是否完整？",
    "truncated tells you the result was limited. Continue only when nextCursor is non-null, keeping the same layers, order, and pixels.": "truncated 表示结果受到数量限制。只有 nextCursor 非 null 时才能继续翻页，翻页时保持 layers、order 和 pixels 不变。",
    "An empty items list means no candidate files matched this query on available layers. A layer-state error means the layer cannot currently be queried. Handle those as different outcomes in your application.": "items 为空，表示在可用图层中没有查到匹配文件；图层状态错误，则表示当前无法查询该图层。应用中应分别展示“没有匹配结果”和“暂不可用”。",
    "Where to find layers, files, and coverage": "如何从索引找到巡天、覆盖范围、精确文件？",
    "Reading Elasticsearch directly? Start with the layer to check its state and available orders, find matching coverage cells, then resolve their file IDs. The three indices below hold the current scan results.": "直接接入 Elasticsearch 时，先检查图层状态和可用阶数，再查覆盖像元，最后用文件 ID 获取文件信息。当前扫描结果分别保存在下面三个索引中。",
    "Read ast_layer_index_v1 for your layer_id. Query only ACTIVE layers, and choose an order listed in available_orders.": "在 ast_layer_index_v1 中按 layer_id 查图层。确认状态为 ACTIVE，并从 available_orders 中选择查询阶数。",
    "Search ast_coverage_index_v1 by layer_id, healpix_order, and healpix_cell. Keep the precision and method alongside each match.": "在 ast_coverage_index_v1 中按 layer_id、healpix_order 和 healpix_cell 查覆盖记录，同时保留每条匹配的精度和提取方法。",
    "Deduplicate source_file_id values and resolve them as file_id in ast_file_index_v1 to retrieve file metadata and source_uri.": "将 source_file_id 去重，再用它匹配 ast_file_index_v1 中的 file_id，获取文件元数据和 source_uri。",
    "For production reads, follow the index contract linked below, including its state checks and result limits. The explorer shows field names and sample documents; it does not connect to your Elasticsearch.": "生产接入时，请遵循下方索引说明中的读取规则，包括状态检查和结果数量限制。这里的浏览器提供字段说明和示例文档，方便对照；它不会连接你的 Elasticsearch。",
    "06 / Understanding results": "06 / 读懂结果",
    "How much does a match tell you?": "查到文件，意味着什么？",
    "Check availability before showing results": "先看图层状态，再展示查询结果",
    "A scan is refreshing this layer. Show it as temporarily unavailable and try again later; this state does not mean the region has no data.": "图层正在更新。请提示用户暂不可用，稍后重试；这并不表示目标天区没有数据。",
    "The scan failed, so this layer is unavailable. Check the error summary and scan evidence before submitting a new scan. Partial results and previous coverage are not returned.": "扫描失败，图层当前不可用。请先查看错误摘要和扫描记录，排查后重新提交。查询不会返回本次未完成的结果或之前的覆盖数据。",
    "Need to investigate a scan? Start with its summary for counts, errors, and the evidence path. Ask your operator for access to the detailed inventory and extraction records at that path. The source snapshot hash identifies the scanned inventory, not the contents of each scientific file.": "需要排查扫描？先从摘要中查看数量、错误和 evidence 路径，再向管理员申请访问该路径下的文件清单和提取记录。源快照哈希用于标识本次扫描的清单，不是各个科学文件内容的校验和。",
    "For complete request fields and response rules, use the ScanPlan, Operator, Query API, and index references in the sidebar.": "完整的请求字段和返回规则，可查阅侧栏中的 ScanPlan、Operator、Query API 和索引参考文档。",
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
    "03 / Submit a scan": "03 / 提交扫描任务",
    "05 / Index explorer": "05 / 索引浏览器",
    "06 / Precision & state": "06 / 精度与状态",
    "Canonical contracts": "接口参考",
    "Index contract": "索引说明",
    "Astro Survey Atlas / Spatial directory": "Astro Survey Atlas / 空间目录",
    "Find the files.": "找到文件。",
    "Keep the sky in context.": "也看懂它覆盖的天空。",
    "Warehouse discovers astronomical files and maintains their current searchable sky coverage. Scan metadata, retain evidence, and resolve explicit HEALPix cells back to original source files. Scientific data stays at its source.": "Warehouse 发现天文文件，维护其当前可搜索的天空覆盖范围。扫描元数据、保留证据，并从显式 HEALPix 像元反查原始源文件。科学数据始终保留在源端。",
    "Submit your first scan": "提交首次扫描",
    "Explore a query response": "查看查询响应",
    "Scan data flow": "扫描数据流",
    "One finite intent": "一个有限的扫描意图",
    "CoverageLayer + source + mode": "CoverageLayer + 数据源 + 模式",
    "Submit scan plan": "提交扫描计划",
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
    "02 / Extraction": "02 / 解析模式",
    "Declare meaning, not processing steps": "声明语义，而非处理步骤",
    "A ScanPlan declares exactly one ExtractionMode for one source and one CoverageLayer. The scanner owns internal step ordering and validates the entire plan before enumeration or credentialed I/O.": "一个 ScanPlan 为一个数据源和一个 CoverageLayer 声明且仅声明一个 ExtractionMode。扫描器负责内部步骤顺序，并在枚举数据源或执行带凭据的 I/O 前验证整个计划。",
    "Image and cube footprints": "图像与数据立方体的覆盖轮廓",
    "Reads supported WCS headers and rasterizes at the required": "读取支持的 WCS 头信息，并按必需的",
    ". Sampled coverage is": "进行栅格化。采样覆盖的精度为",
    "; there is no silent center-point fallback.": "；不会静默退回中心点。",
    "Header position evidence": "从 FITS 头中读取位置",
    "Maps an explicit FITS header position at the required": "按必需的",
    ". Precision is": "映射显式 FITS 头信息位置。精度为",
    ", not an instrument footprint.": "，而非仪器覆盖轮廓。",
    "Catalog occupancy": "星表中的天体分布在哪些像元",
    "Reads configured RA and Dec columns in ICRS degrees and maps them to NESTED cells at": "读取以 ICRS 度数表示的已配置 RA 和 Dec 列，并映射到以下阶数的 NESTED 像元：",
    ". Coverage describes file occupancy, not a per-row object index.": "。覆盖描述文件占用范围，而非逐行对象索引。",
    "Explicit source cells": "文件已有 HEALPix 像元",
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
    "03 / Submission": "03 / 提交扫描任务",
    "From local diagnostic to persisted scan": "先试跑，再保存扫描结果",
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
    "Memory diagnostics do not persist searchable state.": "试跑结果不会写入查询索引。",
    "Evidence is optional only with": "仅在使用",
    "; if declared, use a writable output path. To persist, omit": "时证据可选；若声明证据，请使用可写的输出路径。要持久化，请去掉",
    ", configure the new Warehouse Elasticsearch endpoint and credential references, provision the strict indices, and retain evidence. Scanner startup does not create or recreate indices.": "，配置新 Warehouse Elasticsearch 端点及凭据引用，预先创建严格映射索引，并保留证据。扫描器启动时不会创建或重建索引。",
    "2. Submit a namespaced ScanRequest": "2. 提交命名空间内的 ScanRequest",
    "This complete request wraps the same local catalog intent for Kubernetes. Install infrastructure and the Operator with the supported Helm charts first. Provision the evidence PVC and a Bound source PVC labelled": "这份完整请求将相同的本地星表意图封装为 Kubernetes 请求。请先用受支持的 Helm chart 安装基础设施和 Operator。在以下命名空间内预先创建证据 PVC，以及处于 Bound 状态且带有标签",
    "in": "，命名空间为",
    ". The source claim's": "。数据源 PVC 的",
    "subdirectory is mounted read-only at": "子目录以只读方式挂载到",
    "Copy request": "复制请求",
    "The endpoint and image are example deployment settings, not universal defaults. This request assumes a sink without authentication; authenticated deployments must add namespace-local Secret bindings and plan credential references. Source paths must stay inside the declared source mount, and evidence paths inside the evidence mount. Host paths and cross-namespace claims are not supported.": "端点与镜像仅为部署示例，并非通用默认值。此请求假定 sink 无需认证；需要认证的部署必须添加命名空间内的 Secret 绑定及计划凭据引用。数据源路径必须位于声明的源挂载内，证据路径必须位于证据挂载内。不支持主机路径与跨命名空间的 PVC 引用。",
    "Upstream examples:": "上游示例：",
    "complete local request": "完整本地请求",
    "OSS request with Secret references": "带 Secret 引用的 OSS 请求",
    ", and": "，以及",
    "Helm installation": "Helm 安装",
    "3. Observe execution and evidence": "3. 跟踪进度，查看扫描记录",
    "Submit and monitor / configured cluster required": "提交与监控 / 需要已配置的集群",
    "Copy commands": "复制命令",
    "Wait for a Job name before requesting logs. Request phases are": "请等待 Job 名称出现后再请求日志。请求阶段包括",
    ". The summary reports counts, available orders, source snapshot hash, errors, and evidence path. Detailed inventory, normalized scan, provenance, and errors stay on the evidence volume, not in the browser's initial request.": "。摘要报告计数、可用阶数、源快照哈希、错误与证据路径。详细清单、规范化扫描、来源记录与错误保留在证据卷上，不会包含在浏览器初始请求中。",
    "The Operator creates or adopts an immutable plan ConfigMap and execution-hash-named Job. All resources and credential references remain in the request namespace. The default allowlist watches only": "Operator 创建或接管不可变的计划 ConfigMap 和以执行哈希命名的 Job。所有资源与凭据引用均保留在请求命名空间内。默认允许名单仅监视",
    "; Workspace submissions in": "；Workspace 在",
    "require explicit opt-in. An empty allowlist fails closed.": "中的提交需要显式启用。允许名单为空时拒绝运行。",
    "Reverse lookup": "按天区反查",
    "Ask for cells. Receive file candidates.": "按天区反查",
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
    "These bundled examples illustrate contract behavior. They do not contact a live service, query your indices, or report current survey availability.": "选择一个场景，看看成功、失败或结果受限时会返回什么。以下是内置示例，不会发起真实查询，也不代表当前巡天数据的可用情况。",
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
    "05 / Storage contract": "05 / 存储索引",
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
    "Exact cell assignment for the represented input, such as catalog occupancy. It does not turn a cell intersection into an exact geometry claim.": "输入数据可以准确归入这些像元，例如星表坐标落入的像元。但命中同一像元，不等于已经确认了精确的几何重叠。",
    "Approximate coverage, including sampled WCS rasterization. Preserve that qualification in reverse-lookup results.": "覆盖范围是估算的，例如对 WCS 轮廓采样后得到的像元。展示结果时，请保留这一标记，让用户知道它的精度。",
    "Limited positional or entrypoint evidence, not a full footprint. If only an official URL is known, Assets reads the layer entrypoint; no synthetic file or coverage edge is created.": "有限的位置或入口证据，而非完整覆盖轮廓。如果仅知道官方 URL，Assets 读取图层入口；不会创建虚构文件或覆盖关联边。",
    "Refresh a layer, not a historical index generation": "刷新图层，而非生成历史索引版本",
    "An expiring lease prevents overlapping refreshes. Old layer coverage is deleted before replacement; the layer is unavailable during the update, never silently empty.": "有期限的租约防止并发刷新。替换前删除旧图层覆盖；更新期间图层不可用，绝不会静默返回空结果。",
    "Only verified successful current state is searchable. A successful empty scan is ACTIVE with zero coverage, a valid empty result.": "只有经过验证且成功的当前状态可搜索。成功的空扫描处于 ACTIVE 状态、覆盖数为零，是有效的空结果。",
    "Partial or failed work is unavailable. Any physical partial edges remain hidden behind the state gate; old coverage is not served as a fallback.": "部分完成或失败的结果不可用。任何已写入的部分关联边都被状态检查隐藏；不会回退提供旧覆盖。",
    "Failure is terminal for an execution.": "扫描失败后，先排查，再重新提交。",
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
    "Clipboard unavailable or permission denied. Select the displayed text and copy it manually.": "剪贴板不可用或权限被拒绝。请选择显示的文本并手动复制。",
    "01 / What Warehouse does with your data": "01 / Warehouse 能做什么",
    "02 / Choose how to read your sky coverage": "02 / 四种扫描模式",
    "03 / Run a scan, then check the result": "03 / 提交扫描任务",
    "04 / Ask for cells. Receive file candidates.": "04 / 按天区反查",
    "05 / Where to find layers, files, and coverage": "05 / 存储索引介绍",
    "06 / How much does a match tell you?": "06 / 查到文件，意味着什么？",
    "Mode-specific extraction parameters": "各扫描模式的提取参数",
    "Choose exactly one mode per ScanPlan. The required fields are different; use one alternative below and do not combine mode-specific settings.": "每个 ScanPlan 只能选择一种模式。各模式所需字段不同；请从下方选择一种方案，不要混合不同模式的设置。",
    "Mode": "模式",
    "Required extraction fields": "必填提取字段",
    "Fields to omit": "能省略的字段",
    "Coverage produced": "产生的覆盖",
    "sampled footprint": "采样覆盖轮廓",
    "header position evidence": "头位置证据",
    "Exactly one of": "以下二选一：",
    "occupancy cells;": "占用像元；",
    "must be": "必须为",
    "source cells; preserve explicit NESTED order/ipix": "源像元；保留显式 NESTED order/ipix",
    "The blocks below are minimum extraction alternatives, not fields to merge into one plan. For": "下方代码块是提取参数的最小替代方案，不应合并到同一个计划中。对于",
    ", replace fixed": "，如果每行保存源阶数，请将固定的",
    "with": "替换为",
    "when each row stores its source order; never submit both.": "；不要同时提交两者。",
    "Use the required output order for sampled WCS rasterization.": "使用必填的输出阶数对 WCS 进行采样栅格化。",
    "Use the required output order for explicit header position evidence.": "使用必填的输出阶数记录显式头位置证据。",
    "Provide the catalog columns and set the layer role to": "提供星表列，并将图层角色设为",
    "Preserve source cells with one fixed order or one per-row order column.": "使用一个固定阶数或逐行阶数列，保留源像元。",
    "Every coverage result is stored as": "每条覆盖结果都以",
    "cells: sky pixels identified by": "像元存储：这些天区像素由",
    ", an explicit": "、显式",
    "order, and an": "阶数和",
    ". The precision label describes how those cells were obtained; response truncation is separate.": "。precision 标签说明这些像元是如何得到的；响应截断是独立属性。",
    "Three precision levels describe the evidence behind the stored HEALPix pixels:": "三种精度等级说明了这些已存储 HEALPix 像元背后的证据：",
    "The input identifies the represented HEALPix pixels directly, or a catalog coordinate is deterministically assigned to an occupancy pixel. This is exact cell evidence, not an exact polygon boundary.": "输入直接给出了这些 HEALPix 像元，或星表坐标被确定性地分配到占用像元。这是精确的像元证据，不是精确的多边形边界。",
    "The pixels are sampled or inferred from metadata, such as WCS rasterization. They are useful coverage candidates, but they are not an exact footprint geometry.": "这些像元由元数据采样或推断得到，例如通过 WCS 栅格化得到。它们可作为覆盖候选，但不是精确的 footprint 几何边界。",
    "Only a product location or explicit position is known. This is not a file footprint and does not create synthetic HEALPix cells or a file-level coverage edge.": "已知的只有产品位置或显式位置。这不是文件覆盖轮廓，也不会创建虚构的 HEALPix 像元或文件级覆盖关联边。",
    "Finer source pixels may be coarsened to a lower order. A coarse pixel is never expanded into invented finer pixels. This rule is independent from response truncation.": "更细的源像元可以降到更低阶。粗像元绝不会被扩展成虚构的更细像元。这条规则与响应截断相互独立。"
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
