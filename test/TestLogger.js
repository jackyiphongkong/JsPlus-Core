// ============================================================================
// JsPlus-Core TestLogger - multi-bucket logger (com.killcoding.log)
// ----------------------------------------------------------------------------
// Scope    : LoggerFactory buckets - register(File) builds one bucket per
//            Logger.properties (bucket key = md5(normalizePath(absolute path))),
//            getLogger(SystemName, Class) resolves a bucket, the first bucket is
//            the default one, SystemName goes through SYSTEM_NAME_INDEX
//            (exact case match), a missing SystemName falls back to the file
//            base name, an unknown SystemName falls back to the default bucket,
//            and a duplicate SystemName keeps the first registration. Each
//            bucket carries its own folder and level.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var LoggerFactory = Java.type("com.killcoding.log.LoggerFactory");
var CommonTools = Java.type("com.killcoding.tool.CommonTools");
var System = Java.type("java.lang.System");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var StringCls = Java.type("java.lang.String");
var JsString = Java.type("com.killcoding.jsplus.JsString");

var basePath = System.getProperty("JS_PLUS_PATH");
try {
    if (basePath === null || basePath === undefined || basePath.length === 0) {
        throw "JS_PLUS_PATH is not set - cannot locate TestBase.js";
    }
    load(basePath + "../test/TestBase.js");
} catch (e) {
    print("[ERROR] Failed to load TestBase.js: " + e);
    throw e;
}

var REPORT_TITLE = "JsPlus-Core Logger Multi-Bucket Test";
var REPORT_NAME = "TestLogger-Report.html";

beginTest();

print("=== JsPlus-Core Logger Multi-Bucket Test ===");
print("JS_PLUS_PATH: " + basePath);

var tmpRoot = Files.createTempDirectory("jsplus_logger_").toString();
var fileA = tmpRoot + File.separator + "bucketA.properties";
var fileB = tmpRoot + File.separator + "bucketB.properties";
var fileC = tmpRoot + File.separator + "bucketC.properties";
var fileD = tmpRoot + File.separator + "bucketD.properties";
var folderA = tmpRoot + File.separator + "outA";
var folderB = tmpRoot + File.separator + "outB";

function writeProps(path, content) {
    Files.write(Paths.get(path), JsString.valueOf(content).getBytes("UTF-8"));
}

writeProps(fileA, "SystemName=TestBucketA\nLevel=DEBUG\nFileOut=false\n"
    + "OutLogTimer=5s\nOutLogBatchLimit=100000\nFileOutFolder=" + folderA + "\n");
writeProps(fileB, "SystemName=TestBucketB\nLevel=ERROR\nFileOut=false\n"
    + "OutLogTimer=5s\nOutLogBatchLimit=100000\nFileOutFolder=" + folderB + "\n");
writeProps(fileC, "Level=INFO\nFileOut=false\n"
    + "OutLogTimer=5s\nOutLogBatchLimit=100000\n");
writeProps(fileD, "SystemName=TestBucketA\nLevel=INFO\nFileOut=false\n"
    + "OutLogTimer=5s\nOutLogBatchLimit=100000\n");

var defaultCtx = LoggerFactory.getDefaultContext();

try {
    // C1: the default bucket exists and getLogger(Class) binds to it
    var loggerDefault = LoggerFactory.getLogger(StringCls);
    var ok1 = defaultCtx !== null && loggerDefault.getContext() !== null
        && loggerDefault.getContext().equals(defaultCtx);
    Logger.systemMark("TestLogger.js", "C1");
    check({
        caseId: "C1",
        name: "getDefaultContext() exists; getLogger(Class) binds the default bucket",
        ok: ok1,
        detail: "default=" + (defaultCtx === null ? "null" : defaultCtx.getSystemName())
            + " bound=" + loggerDefault.getContext().equals(defaultCtx),
        objective: "The first registered bucket is the default one, getLogger(Class) keeps working",
        objectiveZh: "首個註冊桶為默認桶，getLogger(Class) 保持可用",
        command: "LoggerFactory.getDefaultContext(); LoggerFactory.getLogger(String.class).getContext()"
    });

    // C2: register(File) keys the bucket by md5(normalizePath(absolute path))
    var ctxA = LoggerFactory.register(new File(fileA));
    var expectedKey = "" + CommonTools.md5(CommonTools.normalizePath(new File(fileA).getAbsolutePath()));
    var ok2 = ctxA !== null && ("" + ctxA.getBucketKey()) === expectedKey;
    Logger.systemMark("TestLogger.js", "C2");
    check({
        caseId: "C2",
        name: "register(File) -> bucket key = md5(normalizePath(absolute path))",
        ok: ok2,
        detail: "key=" + (ctxA === null ? "null" : ctxA.getBucketKey()) + " expected=" + expectedKey,
        objective: "The properties path, not the SystemName, is the bucket identity",
        objectiveZh: "properties 路徑（非 SystemName）是桶的身份",
        command: "LoggerFactory.register(new File(prop)); CommonTools.md5(CommonTools.normalizePath(path))"
    });

    // C3: registering the same file twice returns the same bucket (idempotent)
    var ctxA2 = LoggerFactory.register(new File(fileA));
    var ok3 = ctxA2.equals(ctxA);
    Logger.systemMark("TestLogger.js", "C3");
    check({
        caseId: "C3",
        name: "register(File) is idempotent for the same file",
        ok: ok3,
        detail: "same=" + ok3,
        objective: "Repeated registration does not create a second bucket",
        objectiveZh: "重複註冊不會產生第二個桶",
        command: "LoggerFactory.register(new File(prop)) twice"
    });

    // C4: getLogger(SystemName, Class) resolves the bucket registered for that name
    var ctxB = LoggerFactory.register(new File(fileB));
    var loggerB = LoggerFactory.getLogger("TestBucketB", StringCls);
    var ok4 = ctxB !== null && loggerB.getContext().equals(ctxB);
    Logger.systemMark("TestLogger.js", "C4");
    check({
        caseId: "C4",
        name: "getLogger(\"SystemName\", Class) binds the matching bucket",
        ok: ok4,
        detail: "context=" + loggerB.getContext().getSystemName(),
        objective: "SystemName is the handle to pick a bucket",
        objectiveZh: "SystemName 是取用桶的入口",
        command: "LoggerFactory.getLogger('TestBucketB', String.class)"
    });

    // C5: an unknown SystemName falls back to the default bucket (never breaks)
    var loggerUnknown = LoggerFactory.getLogger("NoSuchBucketZZZ", StringCls);
    var ok5 = loggerUnknown.getContext().equals(defaultCtx);
    Logger.systemMark("TestLogger.js", "C5");
    check({
        caseId: "C5",
        name: "unknown SystemName -> default bucket, no exception",
        ok: ok5,
        detail: "bound default=" + loggerUnknown.getContext().equals(defaultCtx),
        objective: "Business code keeps logging even before its bucket is registered",
        objectiveZh: "桶未註冊時業務照常記錄，不拋異常",
        command: "LoggerFactory.getLogger('NoSuchBucketZZZ', String.class)"
    });

    // C6: a properties file without SystemName uses the file base name
    var ctxC = LoggerFactory.register(new File(fileC));
    var ok6 = ctxC !== null && ("" + ctxC.getSystemName()) === "bucketC";
    Logger.systemMark("TestLogger.js", "C6");
    check({
        caseId: "C6",
        name: "missing SystemName -> file base name (bucketC)",
        ok: ok6,
        detail: "systemName=" + ctxC.getSystemName(),
        objective: "A properties file without SystemName is still addressable",
        objectiveZh: "無 SystemName 的檔案仍可用檔案主名取用",
        command: "LoggerFactory.register(new File('bucketC.properties')).getSystemName()"
    });

    // C7: duplicate SystemName keeps the bucket registered first
    var ctxD = LoggerFactory.register(new File(fileD));
    var ok7 = ctxD !== null && LoggerFactory.getContext("TestBucketA").equals(ctxA)
        && !LoggerFactory.getContext("TestBucketA").equals(ctxD);
    Logger.systemMark("TestLogger.js", "C7");
    check({
        caseId: "C7",
        name: "duplicate SystemName keeps the first bucket, warns and continues",
        ok: ok7,
        detail: "resolved=" + LoggerFactory.getContext("TestBucketA").getSystemName()
            + " dupKey=" + ctxD.getBucketKey(),
        objective: "A duplicated SystemName is reported, the first registration wins",
        objectiveZh: "SystemName 重複時警告並保留先註冊者",
        command: "LoggerFactory.register(duplicate); LoggerFactory.getContext('TestBucketA')"
    });

    // C8: each bucket keeps its own FileOutFolder
    var ok8 = ("" + ctxA.getFileOutFolder()) !== ("" + ctxB.getFileOutFolder())
        && ("" + ctxA.getFileOutFolder()).length > 0;
    Logger.systemMark("TestLogger.js", "C8");
    check({
        caseId: "C8",
        name: "each bucket keeps its own FileOutFolder",
        ok: ok8,
        detail: "A=" + ctxA.getFileOutFolder() + " B=" + ctxB.getFileOutFolder(),
        objective: "Buckets do not share folder/level/queue state",
        objectiveZh: "各桶的目錄 / 級別 / 佇列互不共用",
        command: "ctxA.getFileOutFolder(); ctxB.getFileOutFolder()"
    });

    // C9: each bucket keeps its own level
    var ok9 = ("" + ctxA.getLevel()) === "0" && ("" + ctxB.getLevel()) === "3"
        && LoggerFactory.getLogger("TestBucketB", StringCls).isDebugEnabled() !== true
        && LoggerFactory.getLogger("TestBucketA", StringCls).isDebugEnabled() === true;
    Logger.systemMark("TestLogger.js", "C9");
    check({
        caseId: "C9",
        name: "level is per bucket (A=DEBUG, B=ERROR)",
        ok: ok9,
        detail: "A=" + ctxA.getLevel() + " B=" + ctxB.getLevel()
            + " B.debugEnabled=" + LoggerFactory.getLogger("TestBucketB", StringCls).isDebugEnabled(),
        objective: "Level filtering follows the bucket, not a process-wide static",
        objectiveZh: "級別過濾跟隨所屬桶，而非全進程靜態值",
        command: "ctxA.getLevel(); ctxB.getLevel(); loggerB.isDebugEnabled()"
    });

    // C10: records land in the bucket they were logged through
    var beforeA = Number(ctxA.size());
    var beforeB = Number(ctxB.size());
    LoggerFactory.getLogger("TestBucketA", StringCls).error("logger-bucket-A-message");
    LoggerFactory.getLogger("TestBucketB", StringCls).error("logger-bucket-B-message");
    var ok10 = Number(ctxA.size()) > beforeA && Number(ctxB.size()) > beforeB;
    Logger.systemMark("TestLogger.js", "C10");
    check({
        caseId: "C10",
        name: "log records are queued in the bucket they were logged through",
        ok: ok10,
        detail: "A=" + beforeA + "->" + ctxA.size() + " B=" + beforeB + "->" + ctxB.size(),
        objective: "The log pipeline is per bucket (queue, flusher, file, database)",
        objectiveZh: "日誌管道按桶獨立（佇列、刷盤、檔案、資料庫）",
        command: "loggerA.error('...'); loggerB.error('...'); ctxA.size(); ctxB.size()"
    });

    // C11: "default" is an alias of the default bucket, no warning on lookup
    var aliasCtx = LoggerFactory.getContext("default");
    var aliasLogger = LoggerFactory.getLogger("default", StringCls);
    var ok11 = aliasCtx !== null && aliasCtx.equals(defaultCtx)
        && aliasLogger.getContext().equals(defaultCtx);
    Logger.systemMark("TestLogger.js", "C11");
    check({
        caseId: "C11",
        name: "\"default\" resolves to the default bucket (alias, no fallback warning)",
        ok: ok11,
        detail: "alias=" + (aliasCtx === null ? "null" : aliasCtx.getSystemName())
            + " bound=" + aliasLogger.getContext().equals(defaultCtx),
        objective: "getLogger(Class), getLogger(\"default\", Class) and Logger.systemXxx share one bucket",
        objectiveZh: "getLogger(Class)、getLogger(\"default\", Class) 與 Logger.systemXxx 共用同一桶",
        command: "LoggerFactory.getContext('default'); LoggerFactory.getLogger('default', String.class)"
    });

    // C12: the alias mapping points at the default bucket key
    var index = LoggerFactory.getSystemNameIndex();
    var aliasKey = index === null ? null : index.get("default");
    var ok12 = aliasKey !== null && ("" + aliasKey) === ("" + defaultCtx.getBucketKey());
    Logger.systemMark("TestLogger.js", "C12");
    check({
        caseId: "C12",
        name: "SYSTEM_NAME_INDEX maps \"default\" to the default bucket key",
        ok: ok12,
        detail: "aliasKey=" + aliasKey + " defaultKey=" + defaultCtx.getBucketKey(),
        objective: "The alias is part of the SystemName index, not a special-case branch",
        objectiveZh: "別名寫入 SystemName 索引，而非特判分支",
        command: "LoggerFactory.getSystemNameIndex().get('default')"
    });

} catch (e) {
    Logger.systemMark("TestLogger.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestLogger suite error",
        ok: false,
        detail: "" + e
    });
}

try {
    var dir = new File(tmpRoot);
    var children = dir.listFiles();
    if (children !== null) {
        for (var i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                var inner = children[i].listFiles();
                if (inner !== null) {
                    for (var j = 0; j < inner.length; j++) { inner[j].delete(); }
                }
            }
            children[i].delete();
        }
    }
    dir.delete();
} catch (e) { }

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
