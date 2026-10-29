// ============================================================================
// JsPlus-Core TestXmlParser - XML-to-Map parser (com.killcoding.tool.XmlParser)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestXmlParser.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestXmlParser-Report.html (TestBase
//            format, same as the other suites)
// Scope    : com.killcoding.tool.XmlParser - lightweight XML -> nested
//            Map<String,Object> tree. Covers: constructor parse, getRootName,
//            get() multi-key path + trim, getList()/pathList(), repeated
//            element merge into List, attribute capture as "@name", empty
//            element -> null, xml2Map() direct XML->Map conversion (the
//            'toMap' semantics), put() injection, malformed-XML rejection
//            and deep 3-level path navigation.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var HashMap = Java.type("java.util.HashMap");
var XmlParser = Java.type("com.killcoding.tool.XmlParser");

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

var REPORT_TITLE = "JsPlus-Core XmlParser Test";
var REPORT_NAME = "TestXmlParser-Report.html";

beginTest();

print("=== JsPlus-Core XmlParser Test ===");
print("JS_PLUS_PATH: " + basePath);

try {
    // Sample document: repeated <user> elements (merge -> List), attributes,
    // whitespace text (trim), an empty element and a plain leaf.
    var SAMPLE_XML = "<response code=\"200\" server=\"h2\">\n"
        + "  <status>OK</status>\n"
        + "  <user id=\"1001\">\n"
        + "    <name>jsplus</name>\n"
        + "    <age>28</age>\n"
        + "    <role>admin</role>\n"
        + "    <role>operator</role>\n"
        + "  </user>\n"
        + "  <user id=\"1002\">\n"
        + "    <name>alice</name>\n"
        + "    <age>31</age>\n"
        + "  </user>\n"
        + "  <empty />\n"
        + "  <note>  spaced text  </note>\n"
        + "</response>";

    var parser = null, e1 = "";
    try { parser = new XmlParser(SAMPLE_XML); } catch (e) { e1 = "" + e; }

    // C1: constructor parse + root element name
    Logger.systemMark("TestXmlParser.js", "C1");
    check({
        caseId: "C1",
        name: "XmlParser constructor parses and exposes root element name",
        ok: parser !== null && parser.getRootName() === "response" && e1.length === 0,
        detail: e1.length > 0 ? e1 : ("rootName=" + parser.getRootName()),
        objective: "new XmlParser(xml) builds the Map tree and getRootName() returns the document element",
        objectiveZh: "建構子解析 XML 並 getRootName() 回傳根元素名",
        command: "new XmlParser(SAMPLE_XML).getRootName()"
    });

    // C2: getRoot() non-null and holds a parsed leaf
    var root2 = null, e2 = "";
    try { root2 = parser.getRoot(); } catch (e) { e2 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C2");
    check({
        caseId: "C2",
        name: "getRoot() returns the root Map with parsed content",
        ok: root2 !== null && ("" + root2.get("status")) === "OK" && e2.length === 0,
        detail: e2.length > 0 ? e2 : ("root keys=" + root2.keySet()),
        objective: "getRoot() exposes the whole nested Map<String,Object> tree",
        objectiveZh: "getRoot() 暴露完整巢狀 Map 樹",
        command: "parser.getRoot().get('status')"
    });

    // C3: leaf value via get(), whitespace trimmed
    var v3 = "", e3 = "";
    try { v3 = "" + parser.get("status"); } catch (e) { e3 = "" + e; }
    var v3n = "", e3n = "";
    try { v3n = "" + parser.get("note"); } catch (e) { e3n = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C3");
    check({
        caseId: "C3",
        name: "get() returns leaf text trimmed",
        ok: v3 === "OK" && v3n === "spaced text" && e3.length === 0 && e3n.length === 0,
        detail: ("status=" + v3 + " note='" + v3n + "'"),
        objective: "leaf text content is returned and String results are trimmed",
        objectiveZh: "葉節點文字回傳且 String 結果被 trim",
        command: "parser.get('status') / parser.get('note')"
    });

    // C4: repeated elements merge into a List
    var users4 = null, e4 = "";
    try { users4 = parser.get("user"); } catch (e) { e4 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C4");
    check({
        caseId: "C4",
        name: "repeated <user> elements merge into a List of Maps",
        ok: users4 !== null && users4.size() === 2 && e4.length === 0,
        detail: e4.length > 0 ? e4 : ("users.size=" + users4.size()),
        objective: "same-name siblings are merged into a List<Map<String,Object>>",
        objectiveZh: "同名兄弟元素合併為 List<Map<String,Object>>",
        command: "parser.get('user').size()"
    });

    // C5: elements inside the merged list keep their own fields
    var first5 = null, e5 = "", name5 = "", age5 = "", roleList5 = null;
    try {
        first5 = users4.get(0);
        name5 = "" + first5.get("name");
        age5 = "" + first5.get("age");
        roleList5 = first5.get("role");
    } catch (e) { e5 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C5");
    check({
        caseId: "C5",
        name: "list item exposes its own fields (nested repeated role -> List)",
        ok: name5 === "jsplus" && age5 === "28" && roleList5 !== null && roleList5.size() === 2 && e5.length === 0,
        detail: e5.length > 0 ? e5 : ("name=" + name5 + " age=" + age5 + " roles=" + roleList5.size()),
        objective: "each merged <user> is a Map with its own children; repeated <role> also becomes a List",
        objectiveZh: "合併後的 <user> 各自帶子欄位；重複 <role> 亦成 List",
        command: "users.get(0).get('name')/get('age')/get('role').size()"
    });

    // C6: attributes captured under "@name"
    var attrs6 = null, e6 = "", id6 = "";
    try {
        attrs6 = parser.get("@user");
        id6 = "" + attrs6.get(0).get("id");
    } catch (e) { e6 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C6");
    check({
        caseId: "C6",
        name: "element attributes captured as '@name' Map (merged for repeats)",
        ok: attrs6 !== null && attrs6.size() === 2 && id6 === "1001" && e6.length === 0,
        detail: e6.length > 0 ? e6 : ("@user.size=" + attrs6.size() + " first.id=" + id6),
        objective: "attributes of each element are stored under '@' + short name; repeats merge into a List",
        objectiveZh: "元素屬性以 '@'+短名 儲存；重複元素屬性合併為 List",
        command: "parser.get('@user').get(0).get('id')"
    });

    // C7: attribute-less element -> empty attribute Map
    var sAttrs7 = null, e7 = "";
    try { sAttrs7 = parser.get("@status"); } catch (e) { e7 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C7");
    check({
        caseId: "C7",
        name: "attribute-less element yields an empty attribute Map",
        ok: sAttrs7 !== null && sAttrs7.size() === 0 && e7.length === 0,
        detail: e7.length > 0 ? e7 : ("@status.size=" + sAttrs7.size()),
        objective: "parseAttributes returns an empty Map when the element has no attributes",
        objectiveZh: "無屬性元素回傳空屬性 Map",
        command: "parser.get('@status').size()"
    });

    // C8: empty element -> null value (blank() handling)
    var empty8 = null, e8 = "", emptyNull8 = false;
    try { empty8 = parser.get("empty"); emptyNull8 = (empty8 === null || empty8 === undefined); } catch (e) { e8 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C8");
    check({
        caseId: "C8",
        name: "empty element maps to null (blank text)",
        ok: emptyNull8 && e8.length === 0,
        detail: e8.length > 0 ? e8 : ("empty=" + empty8),
        objective: "elements whose text is blank are stored as null (blank())",
        objectiveZh: "空白文字元素儲存為 null（blank()）",
        command: "parser.get('empty')"
    });

    // C9: path() dot-notation equals get()
    var p9 = "", e9 = "";
    try { p9 = "" + parser.path("status"); } catch (e) { e9 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C9");
    check({
        caseId: "C9",
        name: "path() resolves dot-notation to the same leaf as get()",
        ok: p9 === "OK" && e9.length === 0,
        detail: e9.length > 0 ? e9 : ("path('status')=" + p9),
        objective: "path('a.b.c') splits on '.' and delegates to get()",
        objectiveZh: "path('a.b.c') 以 '.' 分割並委派給 get()",
        command: "parser.path('status')"
    });

    // C10: pathList() returns a List
    var pl10 = null, e10 = "";
    try { pl10 = parser.pathList("user"); } catch (e) { e10 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C10");
    check({
        caseId: "C10",
        name: "pathList() returns the merged List",
        ok: pl10 !== null && pl10.size() === 2 && e10.length === 0,
        detail: e10.length > 0 ? e10 : ("pathList('user').size=" + pl10.size()),
        objective: "pathList() splits on '.' and delegates to getList()",
        objectiveZh: "pathList() 以 '.' 分割並委派給 getList()",
        command: "parser.pathList('user').size()"
    });

    // C11: put() injects a sub-Map under a short key
    var putOk11 = false, e11 = "";
    try {
        var extra = new HashMap();
        extra.put("k", "v");
        parser.put("extra", extra);
        putOk11 = ("" + parser.get("extra", "k")) === "v";
    } catch (e) { e11 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C11");
    check({
        caseId: "C11",
        name: "put() injects a sub-Map readable via get()",
        ok: putOk11 && e11.length === 0,
        detail: e11.length > 0 ? e11 : ("extra.k=" + parser.get("extra", "k")),
        objective: "put(name, map) stores the map under the short name in the root tree",
        objectiveZh: "put(name, map) 以短名存入根樹並可 get() 讀取",
        command: "parser.put('extra', map); parser.get('extra','k')"
    });

    // C12: deep 3-level navigation with multi-key get()
    var DEEP_XML = "<root>\n"
        + "  <level1>\n"
        + "    <level2>\n"
        + "      <leaf>deep-value</leaf>\n"
        + "    </level2>\n"
        + "  </level1>\n"
        + "</root>";
    var deep12 = "", e12 = "";
    try {
        var deepParser = new XmlParser(DEEP_XML);
        deep12 = "" + deepParser.get("level1", "level2", "leaf");
    } catch (e) { e12 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C12");
    check({
        caseId: "C12",
        name: "multi-key get() walks a 3-level nested tree",
        ok: deep12 === "deep-value" && e12.length === 0,
        detail: e12.length > 0 ? e12 : ("deep leaf=" + deep12),
        objective: "get(k1,k2,k3) descends Map levels until the leaf",
        objectiveZh: "get(k1,k2,k3) 沿 Map 層級下探至葉節點",
        command: "new XmlParser(DEEP_XML).get('level1','level2','leaf')"
    });

    // C13: xml2Map() - the XML-to-Map converter used inside the constructor
    // (the 'toMap' semantics: any XML string -> nested Map<String,Object>).
    var map13 = null, e13 = "";
    try { map13 = parser.xml2Map(SAMPLE_XML); } catch (e) { e13 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C13");
    check({
        caseId: "C13",
        name: "xml2Map() parses an XML string directly into a Map",
        ok: map13 !== null && ("" + map13.get("status")) === "OK" && e13.length === 0,
        detail: e13.length > 0 ? e13 : ("xml2Map keys=" + map13.keySet()),
        objective: "xml2Map(xml) converts any XML string to the nested Map tree (the XML->Map / toMap conversion)",
        objectiveZh: "xml2Map(xml) 將任意 XML 字串轉為巢狀 Map 樹（XML→Map / toMap 轉換）",
        command: "parser.xml2Map(SAMPLE_XML).get('status')"
    });

    // C14: malformed XML is rejected at construction
    var bad14 = null, e14 = "";
    try { bad14 = new XmlParser("<not-closed><a></a>"); } catch (e) { e14 = "" + e; }
    Logger.systemMark("TestXmlParser.js", "C14");
    check({
        caseId: "C14",
        name: "malformed XML is rejected at construction",
        ok: bad14 === null && e14.length > 0,
        detail: e14.length > 0 ? e14.substring(0, 120) : "no exception thrown",
        objective: "unbalanced XML throws during new XmlParser(xml)",
        objectiveZh: "不平衡 XML 在建構期拋出例外",
        command: "new XmlParser('<not-closed><a></a>')"
    });

} catch (e) {
    Logger.systemMark("TestXmlParser.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestXmlParser suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
