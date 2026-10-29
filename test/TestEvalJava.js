// ============================================================================
// JsPlus-Core TestEvalJava - dynamic Java compile (source string) + temp jar
// + ClassLoader
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestEvalJava.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestEvalJava-Report.html (TestBase
//            format, same as the other suites)
// Scope    : Console.evalJavaSource(String) / evalJavaSource(String[]) /
//            evalJavaFile(file-or-folder) - dynamic compilation with the JDK
//            system compiler, packaging into a temporary jar under
//            <PROJECT_ROOT>/JsPlus/.tmp/java/<MD5> and returning the dedicated
//            ClassLoader (loader.loadClass("...").newInstance() from JS).
//            Evaluated sources may use the default package or a declared
//            package and may import framework classes (the dynamic classpath
//            covers the whole LibHome).
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var StringType = Java.type("java.lang.String");

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

var REPORT_TITLE = "JsPlus-Core EvalJava Test";
var REPORT_NAME = "TestEvalJava-Report.html";

beginTest();

print("=== JsPlus-Core EvalJava Test ===");
print("JS_PLUS_PATH: " + basePath);

var SRC_HELLO = `
    public class EvalHello {
        public String hello() {
            return "hello-eval";
        }

        public static String staticHello() {
            return "static-hello";
        }
    }`;

var SRC_PACKAGED = `
    package com.killcoding.evaltest;
    public class EvalPackaged {
        public String tag() {
            return "packaged-ok";
        }
    }`;

var SRC_FRAMEWORK_REF = `
    import com.killcoding.tool.CommonTools;
    public class EvalFrameworkRef {
        public String frameworkId() {
            return CommonTools.generateId(6);
        }
    }`;

var SRC_BEAN = `
    public class EvalBean {
        private String name;
        private int count;

        public EvalBean(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() { return name; }
        public int getCount() { return count; }

        public String summarize() { return name + "=" + count; }
    }`;

try {
    // C1: evalJavaSource - default package class, instance + static methods
    var loader1 = Console.evalJavaSource(SRC_HELLO);
    var clazz1 = loader1.loadClass("EvalHello");
    var inst1 = clazz1.newInstance();
    var msg1 = inst1.hello();
    var staticMsg1 = clazz1.getMethod("staticHello").invoke(null);
    Logger.systemMark("TestEvalJava.js", "C1");
    check({
        caseId: "C1",
        name: "evalJavaSource compiles + loads a default-package class",
        ok: clazz1.getName() === "EvalHello" && msg1 === "hello-eval" && staticMsg1 === "static-hello",
        detail: "class=" + clazz1.getName() + " inst=" + msg1 + " static=" + staticMsg1,
        objective: "source string is compiled to a temp jar and loaded via URLClassLoader",
        objectiveZh: "源碼字串被編譯為臨時 jar 並經 URLClassLoader 載入",
        command: "Console.evalJavaSource(source)"
    });

    // C2: evalJavaSource - declared package resolves the qualified class name
    var loader2 = Console.evalJavaSource(SRC_PACKAGED);
    var clazz2 = loader2.loadClass("com.killcoding.evaltest.EvalPackaged");
    var inst2 = clazz2.newInstance();
    Logger.systemMark("TestEvalJava.js", "C2");
    check({
        caseId: "C2",
        name: "evalJavaSource honours a declared package",
        ok: clazz2.getName() === "com.killcoding.evaltest.EvalPackaged" && inst2.tag() === "packaged-ok",
        detail: "class=" + clazz2.getName() + " tag=" + inst2.tag(),
        objective: "a package declaration is applied to the source layout and Class name",
        objectiveZh: "套件宣告套用至源碼擺放與類別名稱",
        command: "Console.evalJavaSource(packaged source)"
    });

    // C3: evalJavaSource - evaluated code can reference framework classes
    var loader3 = Console.evalJavaSource(SRC_FRAMEWORK_REF);
    var clazz3 = loader3.loadClass("EvalFrameworkRef");
    var inst3 = clazz3.newInstance();
    var id3 = inst3.frameworkId();
    Logger.systemMark("TestEvalJava.js", "C3");
    check({
        caseId: "C3",
        name: "evalJavaSource classpath covers the framework (LibHome)",
        ok: id3 !== null && id3.length === 6,
        detail: "frameworkId=" + id3,
        objective: "dynamic -classpath includes the process classpath and LibHome jars",
        objectiveZh: "動態 -classpath 涵蓋程序 classpath 與 LibHome 的 jar",
        command: "Console.evalJavaSource(source using CommonTools)"
    });

    // C4: evalJavaSource - constructor args + private field roundtrip
    var loader4 = Console.evalJavaSource(SRC_BEAN);
    var clazz4 = loader4.loadClass("EvalBean");
    var inst4 = clazz4.getConstructor(StringType, Java.type("int")).newInstance("bean-x", 42);
    var name4 = inst4.getName();
    var count4 = inst4.getCount();
    var summary4 = inst4.summarize();
    Logger.systemMark("TestEvalJava.js", "C4");
    check({
        caseId: "C4",
        name: "evalJavaSource supports constructor args and private fields",
        ok: name4 === "bean-x" && count4 === 42 && summary4 === "bean-x=42",
        detail: "name=" + name4 + " count=" + count4 + " summarize=" + summary4,
        objective: "non-trivial classes (constructor + state) compile and run from a source string",
        objectiveZh: "較複雜類別（建構子 + 狀態）可從源碼字串編譯並運行",
        command: "Console.evalJavaSource(source with constructor + fields)"
    });

    // C5: evalJavaSource - malformed source surfaces the compiler diagnostics
    var c5threw = false, c5msg = "";
    try {
        Console.evalJavaSource("public class BadEval { public void broken( { } }");
    } catch (e) {
        c5threw = true;
        c5msg = "" + e;
    }
    Logger.systemMark("TestEvalJava.js", "C5");
    check({
        caseId: "C5",
        name: "evalJavaSource rejects malformed source with diagnostics",
        ok: c5threw && c5msg.indexOf("compilation failed") >= 0,
        detail: c5msg.length > 0 ? c5msg.substring(0, 120) : "no exception thrown",
        objective: "compile errors raise an Exception carrying the javac diagnostics",
        objectiveZh: "編譯錯誤拋出帶有 javac 診斷訊息的 Exception",
        command: "Console.evalJavaSource(malformed source)"
    });

    // C6: evalJavaSource - blank source is rejected
    var c6threw = false;
    try { Console.evalJavaSource(""); } catch (e) { c6threw = true; }
    Logger.systemMark("TestEvalJava.js", "C6");
    check({
        caseId: "C6",
        name: "blank source is rejected",
        ok: c6threw,
        detail: "sourceThrew=" + c6threw,
        objective: "blank input fails fast with a clear Exception",
        objectiveZh: "空白輸入快速失敗並給出明確 Exception",
        command: "Console.evalJavaSource('')"
    });

    // C7: each eval gets its own ClassLoader (distinct Class for the same source)
    var loader7a = Console.evalJavaSource(SRC_HELLO);
    var loader7b = Console.evalJavaSource(SRC_HELLO);
    var clazz7a = loader7a.loadClass("EvalHello");
    var clazz7b = loader7b.loadClass("EvalHello");
    var inst7a = clazz7a.newInstance();
    var inst7b = clazz7b.newInstance();
    Logger.systemMark("TestEvalJava.js", "C7");
    check({
        caseId: "C7",
        name: "each eval builds an independent ClassLoader",
        ok: loader7a !== loader7b && clazz7a !== clazz7b && inst7a.hello() === "hello-eval" && inst7b.hello() === "hello-eval",
        detail: "loaderA=" + loader7a + " loaderB=" + loader7b,
        objective: "every call packages a fresh jar under a fresh URLClassLoader",
        objectiveZh: "每次呼叫都打包新 jar 並以新的 URLClassLoader 載入",
        command: "Console.evalJavaSource(SRC_HELLO) twice"
    });

    // C8: evalJavaSource(String[]) - a JS array of complete class sources is compiled
    //     in one javac task; each class is usable, classes may reference each other
    var SRC_CLASS_ONE = `
        public class EvalArrayFirst {
            public String who() { return "first"; }
        }`;
    var SRC_CLASS_TWO = `
        public class EvalArraySecond {
            public String who() { return "second"; }
            public String callFirst() { return new EvalArrayFirst().who(); }
        }`;
    var SRC_MULTI_CLASS = [SRC_CLASS_ONE, SRC_CLASS_TWO];
    var loader8 = Console.evalJavaSource(SRC_MULTI_CLASS);
    var clazz8a = loader8.loadClass("EvalArrayFirst");
    var clazz8b = loader8.loadClass("EvalArraySecond");
    var inst8a = clazz8a.newInstance();
    var inst8b = clazz8b.newInstance();
    var who8a = inst8a.who();
    var who8b = inst8b.who();
    var cross8 = inst8b.callFirst();
    Logger.systemMark("TestEvalJava.js", "C8");
    check({
        caseId: "C8",
        name: "evalJavaSource accepts an array of complete classes (multi-class compile)",
        ok: clazz8a.getName() === "EvalArrayFirst"
            && clazz8b.getName() === "EvalArraySecond"
            && who8a === "first" && who8b === "second"
            && cross8 === "first",
        detail: "whoA=" + who8a + " whoB=" + who8b + " cross=" + cross8,
        objective: "each array element is one class; all are compiled in one task and returned in input order",
        objectiveZh: "陣列每元素為一個類別；全部在一次任務編譯並按輸入順序返回",
        command: "Console.evalJavaSource([class1Source, class2Source, ...])"
    });

    // C9: evalJavaFile(folder) - every .java under a folder is compiled in one task
    var DiskFileType = Java.type("com.killcoding.file.DiskFile");
    var folderDir = basePath + "../test/.core-test-tmp/evaljavafolder/";
    var folderSrc1 = `
        public class FolderFirst {
            public String who() { return "folder-first"; }
        }`;
    var folderSrc2 = `
        public class FolderSecond {
            public String who() { return "folder-second"; }
            public String callFirst() { return new FolderFirst().who(); }
        }`;
    new DiskFileType(folderDir + "FolderFirst.java").write(folderSrc1, "UTF-8");
    new DiskFileType(folderDir + "FolderSecond.java").write(folderSrc2, "UTF-8");
    var loader9 = Console.evalJavaFile(folderDir);
    var clazz9a = loader9.loadClass("FolderFirst");
    var clazz9b = loader9.loadClass("FolderSecond");
    var inst9a = clazz9a.newInstance();
    var inst9b = clazz9b.newInstance();
    var who9a = inst9a.who();
    var who9b = inst9b.who();
    var cross9 = inst9b.callFirst();
    Logger.systemMark("TestEvalJava.js", "C9");
    check({
        caseId: "C9",
        name: "evalJavaFile compiles a whole folder of classes",
        ok: clazz9a.getName() === "FolderFirst"
            && clazz9b.getName() === "FolderSecond"
            && who9a === "folder-first" && who9b === "folder-second"
            && cross9 === "folder-first",
        detail: "whoA=" + who9a + " whoB=" + who9b + " cross=" + cross9,
        objective: "a folder path is scanned recursively and all .java files compile in one task",
        objectiveZh: "資料夾路徑會被遞迴掃描並在一次任務內編譯全部 .java 檔案",
        command: "Console.evalJavaFile(folderPath)"
    });

    // C10: evalJavaFile(single .java file) loads the single class through the ClassLoader
    var loader10 = Console.evalJavaFile(folderDir + "FolderFirst.java");
    var clazz10 = loader10.loadClass("FolderFirst");
    var inst10 = clazz10.newInstance();
    Logger.systemMark("TestEvalJava.js", "C10");
    check({
        caseId: "C10",
        name: "evalJavaFile single-file path loads the single class via ClassLoader",
        ok: clazz10.getName() === "FolderFirst" && inst10.who() === "folder-first",
        detail: "class=" + clazz10.getName(),
        objective: "a .java file path compiles that single file (folder form stays compatible)",
        objectiveZh: ".java 檔案路徑只編譯該單一檔案（資料夾形式保持相容）",
        command: "Console.evalJavaFile(singleFilePath)"
    });

    // C11: the evaluated ClassLoader can load any class inside the same eval jar
    //     (here the nested static class, which the top-level class references)
    var SRC_WITH_NESTED = `
        public class EvalNested {
            public static class Helper {
                public String tag() { return "helper-ok"; }
            }
            public String outer() { return "outer-ok"; }
        }`;
    var loaderN = Console.evalJavaSource(SRC_WITH_NESTED);
    var clazzN = loaderN.loadClass("EvalNested");
    var nestedClazz = loaderN.loadClass("EvalNested$Helper");
    var nestedInst = nestedClazz.newInstance();
    var outerMsgN = clazzN.newInstance().outer();
    Logger.systemMark("TestEvalJava.js", "C11");
    check({
        caseId: "C11",
        name: "ClassLoader.loadClass loads classes inside the eval jar",
        ok: loaderN !== null
            && nestedClazz.getName() === "EvalNested$Helper"
            && nestedInst.tag() === "helper-ok"
            && outerMsgN === "outer-ok",
        detail: "loader=" + loaderN + " nested=" + nestedClazz.getName() + " tag=" + nestedInst.tag(),
        objective: "the returned ClassLoader exposes loadClass, so any class in the jar (incl. nested) is reachable",
        objectiveZh: "返回的 ClassLoader 暴露 loadClass，可達 jar 內任何類別（含巢狀類別）",
        command: "loader.loadClass('EvalNested$Helper')"
    });

    // C12: folder eval recurses into sub-folders - classes in child directories
    //     compile together and can reference classes from other directories
    var folderSubDir = basePath + "../test/.core-test-tmp/evaljavafolder-sub/";
    var folderSubChild = folderSubDir + "sub/";
    new DiskFileType(folderSubDir + "FolderAlpha.java").write(
        "public class FolderAlpha { public String who() { return \"alpha\"; } }", "UTF-8");
    new DiskFileType(folderSubChild + "FolderBeta.java").write(
        "public class FolderBeta { public String who() { return \"beta\"; } public String callAlpha() { return new FolderAlpha().who(); } }", "UTF-8");
    var loader12 = Console.evalJavaFile(folderSubDir);
    var clazz12a = loader12.loadClass("FolderAlpha");
    var clazz12b = loader12.loadClass("FolderBeta");
    var inst12a = clazz12a.newInstance();
    var inst12b = clazz12b.newInstance();
    var cross12 = inst12b.callAlpha();
    Logger.systemMark("TestEvalJava.js", "C12");
    check({
        caseId: "C12",
        name: "evalJavaFile recurses into sub-folders (multi-directory compile)",
        ok: clazz12a.getName() === "FolderAlpha"
            && clazz12b.getName() === "FolderBeta"
            && inst12a.who() === "alpha" && inst12b.who() === "beta"
            && cross12 === "alpha",
        detail: "cross=" + cross12,
        objective: "Files.walk recursion covers child directories; one javac task links them all",
        objectiveZh: "Files.walk 遞迴涵蓋子目錄；單次 javac 任務串起全部類別",
        command: "Console.evalJavaFile(folderWithSubFolders)"
    });

} catch (e) {
    Logger.systemMark("TestEvalJava.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestEvalJava suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
