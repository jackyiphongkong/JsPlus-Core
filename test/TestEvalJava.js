// ============================================================================
// JsPlus-Core TestEvalJava - dynamic Java compile (source string) + temp jar
// + ClassLoader
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestEvalJava.js < NUL
// Output   : <PROJECT_ROOT>/test_reports/TestEvalJava-Report.html (TestBase
//            format, same as the other suites)
// Scope    : Console.evalJavaSource(String) - dynamic compilation with the JDK
//            system compiler, packaging into a temporary jar under
//            <PROJECT_ROOT>/work/evaljava and loading through a dedicated
//            URLClassLoader. Evaluated sources may use the default package or
//            a declared package and may import framework classes (the dynamic
//            classpath covers the whole LibHome).
// ============================================================================

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
    var clazz1 = Console.evalJavaSource(SRC_HELLO);
    var inst1 = clazz1.newInstance();
    var msg1 = inst1.hello();
    var staticMsg1 = clazz1.getMethod("staticHello").invoke(null);
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
    var clazz2 = Console.evalJavaSource(SRC_PACKAGED);
    var inst2 = clazz2.newInstance();
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
    var clazz3 = Console.evalJavaSource(SRC_FRAMEWORK_REF);
    var inst3 = clazz3.newInstance();
    var id3 = inst3.frameworkId();
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
    var clazz4 = Console.evalJavaSource(SRC_BEAN);
    var inst4 = clazz4.getConstructor(StringType, Java.type("int")).newInstance("bean-x", 42);
    var name4 = inst4.getName();
    var count4 = inst4.getCount();
    var summary4 = inst4.summarize();
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
    var clazz7a = Console.evalJavaSource(SRC_HELLO);
    var clazz7b = Console.evalJavaSource(SRC_HELLO);
    var inst7a = clazz7a.newInstance();
    var inst7b = clazz7b.newInstance();
    check({
        caseId: "C7",
        name: "each eval builds an independent ClassLoader",
        ok: clazz7a !== clazz7b && inst7a.hello() === "hello-eval" && inst7b.hello() === "hello-eval",
        detail: "classA=" + clazz7a + " classB=" + clazz7b,
        objective: "every call packages a fresh jar under a fresh URLClassLoader",
        objectiveZh: "每次呼叫都打包新 jar 並以新的 URLClassLoader 載入",
        command: "Console.evalJavaSource(SRC_HELLO) twice"
    });

} catch (e) {
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
