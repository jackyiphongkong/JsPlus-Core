// ============================================================================
// JsPlus-Core TestResultMap - ordered result map (com.killcoding.tool.ResultMap)
// ----------------------------------------------------------------------------
// Scope    : ResultMap<K,V> - an AbstractMap whose entrySet keeps insertion
//            order. Covers: put/get/size/remove/clear, capacity + copy
//            constructors, entry iteration order, Entry getKey/getValue/
//            setValue/toString, clone() independence and null handling.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var HashMap = Java.type("java.util.HashMap");
var ResultMap = Java.type("com.killcoding.tool.ResultMap");

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

var REPORT_TITLE = "JsPlus-Core ResultMap Test";
var REPORT_NAME = "TestResultMap-Report.html";

beginTest();

print("=== JsPlus-Core ResultMap Test ===");
print("JS_PLUS_PATH: " + basePath);

try {
    // C1: put/get/size basic contract
    var rm1 = new ResultMap();
    rm1.put("name", "jsplus");
    rm1.put("version", "6.1");
    Logger.systemMark("TestResultMap.js", "C1");
    check({
        caseId: "C1",
        name: "put()/get()/size() basic contract",
        ok: ("" + rm1.get("name")) === "jsplus" && ("" + rm1.get("version")) === "6.1"
            && rm1.size() === 2,
        detail: "name=" + rm1.get("name") + " version=" + rm1.get("version")
            + " size=" + rm1.size(),
        objective: "put() stores key-value pairs; get() retrieves them",
        objectiveZh: "put() 存鍵值；get() 取回",
        command: "rm1.put('name','jsplus'); rm1.get('name')"
    });

    // C2: entrySet() preserves insertion order
    var rm2 = new ResultMap();
    rm2.put("k1", "v1");
    rm2.put("k2", "v2");
    rm2.put("k3", "v3");
    var it2 = rm2.entrySet().iterator();
    var keys2 = "";
    while (it2.hasNext()) {
        var e2 = it2.next();
        keys2 = keys2 + String(e2.getKey());
    }
    Logger.systemMark("TestResultMap.js", "C2");
    check({
        caseId: "C2",
        name: "entrySet() iterates in insertion order",
        ok: keys2 === "k1k2k3",
        detail: "order=" + keys2,
        objective: "entrySet iterator follows insertion order",
        objectiveZh: "entrySet 依插入順序迭代",
        command: "iterate rm2.entrySet(); concatenate keys"
    });

    // C3: Entry getValue()/setValue() contract
    var rm3 = new ResultMap();
    rm3.put("a", "x");
    var it3 = rm3.entrySet().iterator();
    var e3 = it3.next();
    var old3 = e3.setValue("y");
    Logger.systemMark("TestResultMap.js", "C3");
    check({
        caseId: "C3",
        name: "Entry.getValue()/setValue() contract",
        ok: ("" + old3) === "x" && ("" + e3.getValue()) === "y" && ("" + rm3.get("a")) === "y",
        detail: "old=" + old3 + " new=" + e3.getValue() + " map.a=" + rm3.get("a"),
        objective: "Entry.setValue() returns the old value and updates the map",
        objectiveZh: "Entry.setValue() 回傳舊值並更新 map",
        command: "e3.setValue('y'); rm3.get('a')"
    });

    // C4: Entry equals/hashCode - equal keys+values are equal entries
    var rm4a = new ResultMap();
    rm4a.put("k", "v");
    var rm4b = new ResultMap();
    rm4b.put("k", "v");
    var e4a = rm4a.entrySet().iterator().next();
    var e4b = rm4b.entrySet().iterator().next();
    var eq4 = e4a.equals(e4b);
    var hc4 = e4a.hashCode() === e4b.hashCode();
    Logger.systemMark("TestResultMap.js", "C4");
    check({
        caseId: "C4",
        name: "Entry.equals/hashCode reflect key+value",
        ok: eq4 && hc4,
        detail: "equals=" + eq4 + " hashCodeEqual=" + hc4,
        objective: "entries with equal key and value are equal and hash alike",
        objectiveZh: "鍵值均相同的 Entry 相等且雜湊一致",
        command: "entryA.equals(entryB); entryA.hashCode() === entryB.hashCode()"
    });

    // C5: remove(key) removes and returns the old value
    var rm5 = new ResultMap();
    rm5.put("a", "1");
    rm5.put("b", "2");
    var old5 = rm5.remove("a");
    Logger.systemMark("TestResultMap.js", "C5");
    check({
        caseId: "C5",
        name: "remove(key) drops the entry",
        ok: old5 !== null && ("" + old5) === "1" && rm5.size() === 1
            && rm5.get("a") === null,
        detail: "removed=" + old5 + " size=" + rm5.size() + " get(a)=" + rm5.get("a"),
        objective: "remove(key) returns the old value and shrinks the map",
        objectiveZh: "remove(key) 回傳舊值並縮小 map",
        command: "rm5.remove('a')"
    });

    // C6: clear() empties the map
    var rm6 = new ResultMap();
    rm6.put("a", "1");
    rm6.put("b", "2");
    rm6.clear();
    Logger.systemMark("TestResultMap.js", "C6");
    check({
        caseId: "C6",
        name: "clear() empties the map",
        ok: rm6.size() === 0 && rm6.get("a") === null,
        detail: "size=" + rm6.size(),
        objective: "clear() removes all entries",
        objectiveZh: "clear() 移除所有條目",
        command: "rm6.clear(); rm6.size()"
    });

    // C7: copy constructor from an existing Map
    var src7 = new HashMap();
    src7.put("x", "1");
    src7.put("y", "2");
    var rm7 = new ResultMap(src7);
    Logger.systemMark("TestResultMap.js", "C7");
    check({
        caseId: "C7",
        name: "ResultMap(Map) copy constructor",
        ok: rm7.size() === 2 && ("" + rm7.get("x")) === "1" && ("" + rm7.get("y")) === "2",
        detail: "size=" + rm7.size() + " x=" + rm7.get("x") + " y=" + rm7.get("y"),
        objective: "copy constructor copies all entries from the source map",
        objectiveZh: "複製建構子複製來源 map 的全部條目",
        command: "new ResultMap(srcMap)"
    });

    // C8: capacity constructor accepts an initial capacity
    var rm8 = new ResultMap(16);
    rm8.put("a", "1");
    Logger.systemMark("TestResultMap.js", "C8");
    check({
        caseId: "C8",
        name: "ResultMap(int capacity) works",
        ok: rm8.size() === 1 && ("" + rm8.get("a")) === "1",
        detail: "size=" + rm8.size(),
        objective: "capacity constructor accepts initial capacity",
        objectiveZh: "容量建構子接受初始容量",
        command: "new ResultMap(16)"
    });

    // C9: clone() returns an independent copy
    var rm9 = new ResultMap();
    rm9.put("a", "1");
    var clone9 = rm9.clone();
    clone9.put("b", "2");
    var independent9 = (rm9.size() === 1 && clone9.size() === 2);
    Logger.systemMark("TestResultMap.js", "C9");
    check({
        caseId: "C9",
        name: "clone() is an independent copy",
        ok: independent9 && ("" + clone9.get("a")) === "1",
        detail: "original.size=" + rm9.size() + " clone.size=" + clone9.size(),
        objective: "mutating the clone does not affect the original",
        objectiveZh: "修改 clone 不影響原 map",
        command: "var clone = rm9.clone(); clone.put('b','2')"
    });

    // C10: Entry.toString() renders key=value
    var rm10 = new ResultMap();
    rm10.put("k", "v");
    var e10 = rm10.entrySet().iterator().next();
    var s10 = e10.toString();
    Logger.systemMark("TestResultMap.js", "C10");
    check({
        caseId: "C10",
        name: "Entry.toString() renders key=value",
        ok: s10 !== null && s10.indexOf("k") >= 0 && s10.indexOf("v") >= 0,
        detail: "toString=" + s10,
        objective: "toString() contains key and value",
        objectiveZh: "toString() 包含鍵與值",
        command: "entry.toString()"
    });

    // C11: value replacement - put() with an existing key overwrites
    var rm11 = new ResultMap();
    rm11.put("k", "first");
    var old11 = rm11.put("k", "second");
    Logger.systemMark("TestResultMap.js", "C11");
    check({
        caseId: "C11",
        name: "put() on an existing key overwrites and returns the old value",
        ok: old11 !== null && ("" + old11) === "first" && ("" + rm11.get("k")) === "second"
            && rm11.size() === 1,
        detail: "old=" + old11 + " new=" + rm11.get("k") + " size=" + rm11.size(),
        objective: "re-put() updates the value in place",
        objectiveZh: "重複 put() 就地更新值",
        command: "rm11.put('k','first'); rm11.put('k','second')"
    });

    // C12: null key/value are allowed
    var rm12 = new ResultMap();
    rm12.put(null, "nulKey");
    rm12.put("nulVal", null);
    Logger.systemMark("TestResultMap.js", "C12");
    check({
        caseId: "C12",
        name: "null key and null value are accepted",
        ok: ("" + rm12.get(null)) === "nulKey" && rm12.get("nulVal") === null
            && rm12.size() === 2,
        detail: "get(null)=" + rm12.get(null) + " get(nulVal)=" + rm12.get("nulVal")
            + " size=" + rm12.size(),
        objective: "ResultMap tolerates null keys and values",
        objectiveZh: "ResultMap 接受 null 鍵與 null 值",
        command: "rm12.put(null,'nulKey'); rm12.put('nulVal', null)"
    });

    // C13: a wide row (more than INDEX_THRESHOLD columns) keeps order and value
    var rm13 = new ResultMap();
    var i13;
    for (i13 = 1; i13 <= 43; i13++) {
        rm13.put("col" + i13, "v" + i13);
    }
    var it13 = rm13.entrySet().iterator();
    var n13 = 0;
    var order13 = "";
    while (it13.hasNext()) {
        var e13 = it13.next();
        order13 = order13 + ((n13 === 0) ? "" : ",") + String(e13.getKey());
        n13 = n13 + 1;
    }
    var old13 = rm13.put("col20", "replaced");
    var head13 = order13.indexOf("col1,col2,col3,col4,col5,col6,col7,col8") === 0;
    var tail13 = order13.lastIndexOf("col43") === (order13.length - 5);
    Logger.systemMark("TestResultMap.js", "C13");
    check({
        caseId: "C13",
        name: "43-column row keeps insertion order, values and overwrite",
        ok: n13 === 43 && head13 && tail13 && ("" + rm13.get("col43")) === "v43"
            && ("" + old13) === "v20" && ("" + rm13.get("col20")) === "replaced"
            && rm13.size() === 43,
        detail: "count=" + n13 + " head=" + head13 + " tail=" + tail13
            + " col43=" + rm13.get("col43") + " old20=" + old13
            + " col20=" + rm13.get("col20") + " size=" + rm13.size(),
        objective: "on a wide row the indexed put keeps order, values and overwrite semantics",
        objectiveZh: "寬結果列在索引化 put 下仍保持順序、取值與覆寫語義",
        command: "43 puts, iterate entrySet, then put('col20','replaced')"
    });

    // C14: removal through the iterator must not leave stale positions
    var rm14 = new ResultMap();
    var i14;
    for (i14 = 1; i14 <= 43; i14++) {
        rm14.put("col" + i14, "v" + i14);
    }
    var it14 = rm14.entrySet().iterator();
    while (it14.hasNext()) {
        var e14 = it14.next();
        if (("" + e14.getKey()) === "col5") {
            it14.remove();
        }
    }
    rm14.put("tail", "tv");
    Logger.systemMark("TestResultMap.js", "C14");
    check({
        caseId: "C14",
        name: "iterator removal invalidates positions; the next put stays correct",
        ok: rm14.size() === 43 && rm14.get("col5") === null
            && ("" + rm14.get("col43")) === "v43"
            && ("" + rm14.get("tail")) === "tv",
        detail: "size=" + rm14.size() + " col5=" + rm14.get("col5")
            + " col43=" + rm14.get("col43") + " tail=" + rm14.get("tail"),
        objective: "after entrySet iterator remove() the index is rebuilt, not reused stale",
        objectiveZh: "entrySet 迭代器移除後索引重建而非沿用舊位置",
        command: "remove col5 via iterator, put('tail','tv')"
    });

    // C15: clear() drops the index and a later wide row rebuilds it
    var rm15 = new ResultMap();
    var i15;
    for (i15 = 1; i15 <= 43; i15++) {
        rm15.put("col" + i15, "v" + i15);
    }
    rm15.put("stale", "sv");
    rm15.clear();
    var i15b;
    for (i15b = 1; i15b <= 20; i15b++) {
        rm15.put("col" + i15b, "v" + i15b);
    }
    Logger.systemMark("TestResultMap.js", "C15");
    check({
        caseId: "C15",
        name: "clear() drops the index; a later wide row reads back correctly",
        ok: rm15.size() === 20 && ("" + rm15.get("col1")) === "v1"
            && ("" + rm15.get("col20")) === "v20" && rm15.get("stale") === null,
        detail: "size=" + rm15.size() + " col1=" + rm15.get("col1")
            + " col20=" + rm15.get("col20") + " stale=" + rm15.get("stale"),
        objective: "clear() resets the index so a rebuilt row has no stale keys",
        objectiveZh: "clear() 清除索引，重建列不殘留舊鍵",
        command: "43 puts, put('stale'), clear(), then 20 puts"
    });

} catch (e) {
    Logger.systemMark("TestResultMap.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestResultMap suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
