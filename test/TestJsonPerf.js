// ============================================================================
// JsPlus-Core TestJsonPerf - org.json (JSONObject/JSONArray) vs
//                            com.killcoding.jsplus.Json  (parse + format)
// ----------------------------------------------------------------------------
// Run      : test/TestJsonPerf.bat   (adds the org.json jar to the classpath;
//            NOT part of TestAll - a timing suite must not run inside CI)
// Output   : console table + <PROJECT_ROOT>/docs/latest/report/JsonPerf.md
//
// Why batched with a sleep between batches:
//   Json.parse borrows a console from TextScriptConsole and freed() returns it
//   only after freedDelayMs (default 10ms) via a scheduler. Hammering parse in
//   one tight loop therefore exhausts the pool instead of measuring the parser.
//   Each batch is measured as one block, and a gap between batches lets the
//   freed consoles come back, so the numbers reflect parsing rather than pool
//   starvation. The final stress case deliberately removes the gap to show what
//   actually happens under a tight loop.
//
// Fairness notes:
//   - org.json side measures `new JSONObject(text)` / `new JSONArray(text)`
//     only. ext.Json.parse does that PLUS a recursive walk into
//     LinkedHashMap/ArrayList, so the real old path is slower than the number
//     shown here - the comparison is biased in org.json's favour on purpose.
//   - Both sides are wrapped in a JS closure, so the call overhead is identical.
//   - This engine runs in interpreter-only mode (no Truffle native compilation
//     in this LibHome), which is the production configuration here.
// ============================================================================

var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var Json = Java.type("com.killcoding.jsplus.Json");
var JSONObject = Java.type("org.json.JSONObject");
var JSONArray = Java.type("org.json.JSONArray");
var LinkedHashMap = Java.type("java.util.LinkedHashMap");
var ArrayList = Java.type("java.util.ArrayList");
var TextScriptConsole = Java.type("com.killcoding.jsplus.TextScriptConsole");
var JsString = Java.type("com.killcoding.jsplus.JsString");
var FileOutputStream = Java.type("java.io.FileOutputStream");
var OutputStreamWriter = Java.type("java.io.OutputStreamWriter");

var basePath = System.getProperty("JS_PLUS_PATH");

function str(s) {
    return JsString.valueOf(s).asString();
}

function fmt(v) {
    var d = Number(v);
    var r = Math.round(d * 100) / 100;
    return "" + r;
}

function padRight(s, n) {
    var t = str(s);
    while (t.length < n) {
        t = t + " ";
    }
    return t;
}

// ---------------------------------------------------------------- samples
var textSmall = '{"a":1,"b":"x","c":true,"d":null}';

function buildMedium() {
    var tags = [];
    for (var i = 0; i < 20; i++) {
        tags.push('"tag-' + i + '"');
    }
    return '{"id":123,"name":"JsPlus","tags":[' + tags.join(",") + '],"nested":{"k":1,"v":"z"}}';
}

function buildLarge(n) {
    var items = [];
    for (var i = 0; i < n; i++) {
        items.push('{"id":' + i + ',"name":"item-' + i + '","ok":' + (i % 2 === 0) + ',"score":' + (i % 100) + '}');
    }
    return '{"rows":[' + items.join(",") + '],"total":' + n + '}';
}

var textMedium = buildMedium();
var textLarge = buildLarge(100);

var arraySmall = '["a","b","c"]';
var arrayLarge = (function () {
    var items = [];
    for (var i = 0; i < 50; i++) {
        items.push('"v' + i + '"');
    }
    return "[" + items.join(",") + "]";
})();

// A Map shaped exactly like textMedium, used for the serialization comparison.
function buildMap() {
    var m = new LinkedHashMap();
    m.put("id", 123);
    m.put("name", "JsPlus");
    var tags = new ArrayList();
    for (var i = 0; i < 20; i++) {
        tags.add("tag-" + i);
    }
    m.put("tags", tags);
    var nested = new LinkedHashMap();
    nested.put("k", 1);
    nested.put("v", "z");
    m.put("nested", nested);
    return m;
}

var mapMedium = buildMap();

print("=== JsPlus-Core Json performance: org.json vs com.killcoding.jsplus.Json ===");
print("engine           : " + TextScriptConsole.getEngineName());
print("console poolSize : " + TextScriptConsole.getDefaultPool().getMaxPoolSize());
print("freedDelayMs     : " + TextScriptConsole.getDefaultPool().getFreedDelayMs());
print("textSmall  bytes : " + str(textSmall).length);
print("textMedium bytes : " + str(textMedium).length);
print("textLarge  bytes : " + str(textLarge).length);
print("");

var results = [];

function record(label, per, batches, gapMs, fn) {
    // warm-up: let class loading / first Source build / engine init settle
    for (var w = 0; w < 4; w++) {
        try { fn(); } catch (e) { }
    }
    var totalUs = 0;
    var minUs = 1e9;
    var maxUs = 0;
    var fails = 0;
    var calls = 0;
    for (var b = 0; b < batches; b++) {
        if (b > 0 && gapMs > 0) {
            try { Thread.sleep(gapMs); } catch (e) { }
        }
        var t0 = System.nanoTime();
        for (var i = 0; i < per; i++) {
            var ok = false;
            try {
                var r = fn();
                ok = (r !== null && r !== undefined);
            } catch (e) {
                ok = false;
            }
            calls++;
            if (!ok) {
                fails++;
            }
        }
        var t1 = System.nanoTime();
        var us = Number(t1 - t0) / 1000.0 / per;
        totalUs += us;
        if (us < minUs) { minUs = us; }
        if (us > maxUs) { maxUs = us; }
    }
    var avg = totalUs / batches;
    var rec = { label: str(label), avg: avg, min: minUs, max: maxUs, fails: fails, calls: calls };
    results.push(rec);
    print(padRight(rec.label, 34)
        + "avg=" + padRight(fmt(avg), 9) + "us  min=" + padRight(fmt(minUs), 8)
        + "  max=" + padRight(fmt(maxUs), 9) + "  fail=" + fails + "/" + calls);
    return rec;
}

// ---------------------------------------------------------------- parse: small
print("--- parse / small (37 bytes) ---");
record("org.json new JSONObject(small)", 8, 10, 30, function () {
    return new JSONObject(textSmall);
});
record("jsplus  Json.parse(small)", 8, 10, 30, function () {
    return Json.parse(textSmall);
});

// ---------------------------------------------------------------- parse: medium
print("--- parse / medium ---");
record("org.json new JSONObject(medium)", 5, 10, 30, function () {
    return new JSONObject(textMedium);
});
record("jsplus  Json.parse(medium)", 5, 10, 30, function () {
    return Json.parse(textMedium);
});

// ---------------------------------------------------------------- parse: large
print("--- parse / large ---");
record("org.json new JSONObject(large)", 3, 8, 30, function () {
    return new JSONObject(textLarge);
});
record("jsplus  Json.parse(large)", 3, 8, 30, function () {
    return Json.parse(textLarge);
});

// ---------------------------------------------------------------- parse: arrays
print("--- parse / array ---");
record("org.json new JSONArray(small)", 8, 10, 30, function () {
    return new JSONArray(arraySmall);
});
record("jsplus  Json.parse(arraySmall)", 8, 10, 30, function () {
    return Json.parse(arraySmall);
});
record("org.json new JSONArray(large)", 3, 8, 30, function () {
    return new JSONArray(arrayLarge);
});
record("jsplus  Json.parse(arrayLarge)", 3, 8, 30, function () {
    return Json.parse(arrayLarge);
});

// ---------------------------------------------------------------- serialize
print("--- serialize / medium Map ---");
record("org.json new JSONObject(map).toString()", 5, 10, 30, function () {
    return new JSONObject(mapMedium).toString();
});
record("jsplus  Json.format(map)", 5, 10, 30, function () {
    return Json.format(mapMedium);
});

// ---------------------------------------------------------------- stress
print("--- stress: tight loop, no gap (pool starvation?) ---");
var stressOk = 0;
var stressFail = 0;
var t0 = System.nanoTime();
for (var s = 0; s < 150; s++) {
    var sv = Json.parse(textSmall);
    if (sv === null) {
        stressFail++;
    } else {
        stressOk++;
    }
}
var t1 = System.nanoTime();
var stressUs = Number(t1 - t0) / 1000.0 / 150;
print(padRight("jsplus  Json.parse x150 no gap", 34)
    + "avg=" + padRight(fmt(stressUs), 9) + "us  ok=" + stressOk + " fail=" + stressFail);

// ---------------------------------------------------------------- decomposition
// Where does Json.parse actually spend its time? Four variants on the SAME
// console context and the same input, so the difference isolates one factor:
//   evalOnly  - a trivial script          -> the fixed cost of evalScript itself
//   parseOnly - evalScript + JSON.parse   -> + the native JSON parser, no conversion
//   prewarm   - a function defined once, then a ~62-char call script
//               -> replaces the 500-char production script with a short one
//   prewarm   - call script only          -> the shipped implementation
//   redefine  - definition + call each time -> the pre-optimisation behaviour
// prewarm vs redefine is exactly the "one global context, pre-warmed function"
// question: how much of the cost is re-parsing the converter definition every call.
print("");
print("--- decomposition (same context, same payload, no freed()) ---");

var ctx = TextScriptConsole.getDefaultPool().getThreadContext();
var IN_NAME = "__jsplus_json_input";
var BOX_NAME = "__jsplus_json_box";
var FN_NAME = "__jsplus_json_conv";
var box = new LinkedHashMap();

// Reflected straight out of Json so the measurement tracks the shipped
// implementation: the definition script (runs once per context) and the call
// script (runs on every parse).
var JsonCls = Java.type("java.lang.Class").forName("com.killcoding.jsplus.Json");
var defineField = JsonCls.getDeclaredField("PARSE_DEFINE_SCRIPT");
defineField.setAccessible(true);
var DEFINE_PROD = "" + defineField.get(null);
var callField = JsonCls.getDeclaredField("PARSE_CALL_SCRIPT");
callField.setAccessible(true);
var CALL_PROD = "" + callField.get(null);

var PARSE_ONLY_SCRIPT = BOX_NAME + ".put('v',JSON.parse(" + IN_NAME + "));";

ctx.put(BOX_NAME, box);
ctx.evalScript(DEFINE_PROD);
print("define script (once): " + DEFINE_PROD.length + " chars, call script (per call): " + CALL_PROD.length + " chars");

record("evalOnly  evalScript('1+1')", 8, 8, 0, function () {
    return ctx.evalScript("1+1");
});
record("parseOnly evalScript + JSON.parse", 8, 8, 0, function () {
    ctx.put(IN_NAME, textSmall);
    return ctx.evalScript(PARSE_ONLY_SCRIPT);
});
record("prewarm   call script only", 8, 8, 0, function () {
    ctx.put(IN_NAME, textSmall);
    return ctx.evalScript(CALL_PROD);
});
record("redefine  define+call each time", 8, 8, 0, function () {
    ctx.put(IN_NAME, textSmall);
    ctx.put(BOX_NAME, box);
    ctx.evalScript(DEFINE_PROD);
    return ctx.evalScript(CALL_PROD);
});

// Same four on the medium payload, where conversion touches more host objects.
record("evalOnly  medium", 5, 8, 0, function () {
    return ctx.evalScript("1+1");
});
record("parseOnly medium", 5, 8, 0, function () {
    ctx.put(IN_NAME, textMedium);
    return ctx.evalScript(PARSE_ONLY_SCRIPT);
});
record("prewarm   medium", 5, 8, 0, function () {
    ctx.put(IN_NAME, textMedium);
    return ctx.evalScript(CALL_PROD);
});
record("redefine  medium", 5, 8, 0, function () {
    ctx.put(IN_NAME, textMedium);
    ctx.put(BOX_NAME, box);
    ctx.evalScript(DEFINE_PROD);
    return ctx.evalScript(CALL_PROD);
});

// ---------------------------------------------------------------- ratio
print("");
print("--- parse speed ratio (jsplus / org.json, same payload) ---");
function find(label) {
    for (var i = 0; i < results.length; i++) {
        if (results[i].label === label) { return results[i]; }
    }
    return null;
}
function ratio(jsLabel, orgLabel) {
    var a = find(jsLabel);
    var b = find(orgLabel);
    if (a === null || b === null) { return null; }
    return a.avg / b.avg;
}
var pairs = [
    ["small", "jsplus  Json.parse(small)", "org.json new JSONObject(small)"],
    ["medium", "jsplus  Json.parse(medium)", "org.json new JSONObject(medium)"],
    ["large", "jsplus  Json.parse(large)", "org.json new JSONObject(large)"],
    ["array small", "jsplus  Json.parse(arraySmall)", "org.json new JSONArray(small)"],
    ["array large", "jsplus  Json.parse(arrayLarge)", "org.json new JSONArray(large)"],
    ["serialize", "jsplus  Json.format(map)", "org.json new JSONObject(map).toString()"]
];
for (var p = 0; p < pairs.length; p++) {
    var rr = ratio(pairs[p][1], pairs[p][2]);
    if (rr !== null) {
        print(padRight(pairs[p][0], 14) + "x" + fmt(rr));
    }
}

// ---------------------------------------------------------------- report
var md = [];
md.push("# JsPlus-Core Json perf: org.json vs com.killcoding.jsplus.Json");
md.push("");
md.push("engine: `" + TextScriptConsole.getEngineName() + "`, poolSize="
    + TextScriptConsole.getDefaultPool().getMaxPoolSize()
    + ", freedDelayMs=" + TextScriptConsole.getDefaultPool().getFreedDelayMs() + " (interpreter-only runtime)");
md.push("");
md.push("Payload sizes: small=" + str(textSmall).length
    + "B, medium=" + str(textMedium).length + "B, large=" + str(textLarge).length + "B");
md.push("");
md.push("| case | avg us | min us | max us | fail/calls |");
md.push("| --- | --- | --- | --- | --- |");
for (var k = 0; k < results.length; k++) {
    var r = results[k];
    md.push("| " + r.label + " | " + fmt(r.avg) + " | " + fmt(r.min) + " | " + fmt(r.max)
        + " | " + r.fails + "/" + r.calls + " |");
}
md.push("");
md.push("| payload | jsplus / org.json |");
md.push("| --- | --- |");
for (var q = 0; q < pairs.length; q++) {
    var rr2 = ratio(pairs[q][1], pairs[q][2]);
    if (rr2 !== null) {
        md.push("| " + pairs[q][0] + " | " + fmt(rr2) + "x |");
    }
}
md.push("");
md.push("Stress (150 consecutive Json.parse, no gap): ok=" + stressOk
    + ", fail=" + stressFail + ", avg=" + fmt(stressUs) + "us");
md.push("");
md.push("Notes: org.json side is `new JSONObject(text)` / `new JSONArray(text)` only -");
md.push("ext.Json.parse additionally walks the tree into LinkedHashMap/ArrayList, so the");
md.push("real old path is slower than shown; the comparison is biased toward org.json.");

var outPath = basePath + "../docs/latest/report/JsonPerf.md";
var w = new OutputStreamWriter(new FileOutputStream(str(outPath)), "UTF-8");
w.write(md.join("\n") + "\n");
w.close();
print("");
print("Report written to " + str(outPath));
