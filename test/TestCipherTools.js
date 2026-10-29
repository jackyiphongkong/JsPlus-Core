// ============================================================================
// JsPlus-Core TestCipherTools - base cipher utilities
// (com.killcoding.tool.Base64Tools + com.killcoding.tool.CipherTools)
// ----------------------------------------------------------------------------
// Scope    : Base64Tools encode/decode (bytes, char[], String, lines) and
//            CipherTools AES/DES/3DES/RSA encrypt-decrypt roundtrips.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var Arrays = Java.type("java.util.Arrays");
var Base64Tools = Java.type("com.killcoding.tool.Base64Tools");
var CipherTools = Java.type("com.killcoding.tool.CipherTools");

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

var REPORT_TITLE = "JsPlus-Core CipherTools Test";
var REPORT_NAME = "TestCipherTools-Report.html";

beginTest();

print("=== JsPlus-Core CipherTools Test ===");
print("JS_PLUS_PATH: " + basePath);

// Plain text including non-ASCII characters (UTF-8 roundtrip)
var PLAIN = "JsPlus-Core 加密測試 hello 1234 !@#";

// NOTE: GraalJS String has no getBytes(); interop converts java.lang.String
// return values into JS primitives, so any String.valueOf(...).getBytes()
// chain fails. JsString wraps java.lang.String in a plain object so every
// String method (getBytes etc.) stays callable from JS.
var JsString = Java.type("com.killcoding.jsplus.JsString");
var toUtf8 = function (s) { return JsString.valueOf(s).getBytes("UTF-8"); };
var fromUtf8 = function (b) { return new JsString(b, "UTF-8").asString(); };
var bytesEqual = function (a, b) {
    if (a === null || b === null || a.length !== b.length) { return false; }
    for (var i = 0; i < a.length; i++) { if (a[i] !== b[i]) { return false; } }
    return true;
};

try {
    // --- Base64Tools ------------------------------------------------------
    // C1: encode/decode byte[] roundtrip
    var raw1 = toUtf8(PLAIN);
    var enc1 = Base64Tools.encode(raw1);
    var dec1 = Base64Tools.decode(enc1);
    var ok1 = enc1 !== null && enc1.length > 0
        && fromUtf8(dec1) === PLAIN;
    Logger.systemMark("TestCipherTools.js", "C1");
    check({
        caseId: "C1",
        name: "Base64 encode(byte[])/decode(char[]) roundtrip",
        ok: ok1,
        detail: "encLen=" + (enc1 === null ? 0 : enc1.length) + " decLen=" + (dec1 === null ? 0 : dec1.length),
        objective: "encode produces a char[] that decode reverses",
        objectiveZh: "encode 產生的 char[] 可被 decode 還原",
        command: "Base64Tools.decode(Base64Tools.encode(bytes))"
    });

    // C2: encodeString/decodeString String roundtrip
    var s2 = Base64Tools.encodeString(PLAIN);
    var back2 = Base64Tools.decodeString(s2);
    Logger.systemMark("TestCipherTools.js", "C2");
    check({
        caseId: "C2",
        name: "encodeString()/decodeString() String roundtrip",
        ok: s2 !== null && s2.length > 0 && back2 === PLAIN,
        detail: "encoded=" + s2,
        objective: "String helpers wrap the byte[] codec",
        objectiveZh: "字串輔助方法包裝 byte[] 編解碼",
        command: "Base64Tools.decodeString(Base64Tools.encodeString(plain))"
    });

    // C3: encodeLines/decodeLines multiline roundtrip
    var longText3 = "line1\nline2\nline3 with unicode 中文";
    var enc3 = Base64Tools.encodeLines(toUtf8(longText3));
    var dec3 = Base64Tools.decodeLines(enc3);
    Logger.systemMark("TestCipherTools.js", "C3");
    check({
        caseId: "C3",
        name: "encodeLines()/decodeLines() multiline roundtrip",
        ok: enc3 !== null && fromUtf8(dec3) === longText3,
        detail: "encLines=" + enc3,
        objective: "line-wrapped base64 still decodes to the original text",
        objectiveZh: "換行包裝的 base64 仍可還原原文",
        command: "Base64Tools.decodeLines(Base64Tools.encodeLines(bytes))"
    });

    // C4: decode(String) overload accepts a String
    var enc4 = Base64Tools.encodeString(PLAIN);   // String helper returns a String
    var dec4 = Base64Tools.decode(enc4);          // decode(String) overload -> byte[]
    Logger.systemMark("TestCipherTools.js", "C4");
    check({
        caseId: "C4",
        name: "decode(String) overload accepts a String",
        ok: dec4 !== null && fromUtf8(dec4) === PLAIN,
        detail: "decLen=" + dec4.length,
        objective: "decode(String) parses the same payload",
        objectiveZh: "decode(String) 解析相同內容",
        command: "Base64Tools.decode(encodedString)"
    });

    // --- CipherTools AES --------------------------------------------------
    // C5: AES ECB encrypt/decrypt roundtrip (UTF-8)
    var aesKey5 = "my-aes-test-key-000";
    var enc5 = CipherTools.AESEncrypt(aesKey5, toUtf8(PLAIN));
    var dec5 = CipherTools.AESDecrypt(aesKey5, enc5);
    Logger.systemMark("TestCipherTools.js", "C5");
    check({
        caseId: "C5",
        name: "AESEncrypt()/AESDecrypt() roundtrip (ECB/PKCS5)",
        ok: enc5 !== null && enc5.length > 0 && fromUtf8(dec5) === PLAIN,
        detail: "encLen=" + enc5.length,
        objective: "AES ECB encrypt then decrypt returns the original UTF-8 text",
        objectiveZh: "AES ECB 加密後解密還原 UTF-8 原文",
        command: "CipherTools.AESDecrypt(key, CipherTools.AESEncrypt(key, bytes))"
    });

    // C6: generateAESKey returns exactly 16 bytes
    var key6 = CipherTools.generateAESKey("any-seed-text");
    Logger.systemMark("TestCipherTools.js", "C6");
    check({
        caseId: "C6",
        name: "generateAESKey() returns 16 bytes",
        ok: key6 !== null && key6.length === 16,
        detail: "keyLen=" + (key6 === null ? 0 : key6.length),
        objective: "SHA-256 digest truncated to 16 bytes",
        objectiveZh: "SHA-256 摘要截為 16 位元組",
        command: "CipherTools.generateAESKey('any-seed-text')"
    });

    // C7: AES with different keys produces different ciphertext
    var enc7a = CipherTools.AESEncrypt("key-AAAAAAAA", toUtf8(PLAIN));
    var enc7b = CipherTools.AESEncrypt("key-BBBBBBBB", toUtf8(PLAIN));
    Logger.systemMark("TestCipherTools.js", "C7");
    check({
        caseId: "C7",
        name: "different AES keys yield different ciphertext",
        ok: !bytesEqual(enc7a, enc7b),
        detail: "lenA=" + enc7a.length + " lenB=" + enc7b.length,
        objective: "key material changes the encrypted output",
        objectiveZh: "不同金鑰產生不同密文",
        command: "AESEncrypt(keyA) != AESEncrypt(keyB)"
    });

    // --- CipherTools DES / 3DES ------------------------------------------
    // C8: DES CBC roundtrip
    var desKey8 = "12345678";   // 8-byte DES key
    var desIv8 = "87654321";    // 8-byte IV
    var enc8 = CipherTools.DESEncrypt(desKey8, desIv8, toUtf8(PLAIN));
    var dec8 = CipherTools.DESDecrypt(desKey8, desIv8, enc8);
    Logger.systemMark("TestCipherTools.js", "C8");
    check({
        caseId: "C8",
        name: "DESEncrypt()/DESDecrypt() roundtrip (CBC/PKCS5)",
        ok: enc8 !== null && enc8.length > 0 && fromUtf8(dec8) === PLAIN,
        detail: "encLen=" + enc8.length,
        objective: "DES CBC encrypt then decrypt returns the original text",
        objectiveZh: "DES CBC 加密後解密還原原文",
        command: "CipherTools.DESDecrypt(k, iv, CipherTools.DESEncrypt(k, iv, bytes))"
    });

    // C9: 3DES CBC roundtrip
    var des3Key9 = "123456781234567812345678";   // 24-byte 3DES key
    var des3Iv9 = "87654321";                    // 8-byte IV
    var enc9 = CipherTools._3DESEncrypt(des3Key9, des3Iv9, toUtf8(PLAIN));
    var dec9 = CipherTools._3DESDecrypt(des3Key9, des3Iv9, enc9);
    Logger.systemMark("TestCipherTools.js", "C9");
    check({
        caseId: "C9",
        name: "_3DESEncrypt()/_3DESDecrypt() roundtrip (CBC/PKCS5)",
        ok: enc9 !== null && enc9.length > 0 && fromUtf8(dec9) === PLAIN,
        detail: "encLen=" + enc9.length,
        objective: "3DES CBC encrypt then decrypt returns the original text",
        objectiveZh: "3DES CBC 加密後解密還原原文",
        command: "CipherTools._3DESDecrypt(k, iv, CipherTools._3DESEncrypt(k, iv, bytes))"
    });

    // --- CipherTools RSA --------------------------------------------------
    // C10: RSA key generation + public-encrypt/private-decrypt roundtrip
    var seed10 = toUtf8("jsplus-rsa-seed");
    var priv10 = CipherTools.RSAPrivateKeyGen(seed10);
    var pub10 = CipherTools.RSAPublieKeyGen(seed10);
    var pubKey10 = CipherTools.RSAPublicKeyFrom(pub10);
    var privKey10 = CipherTools.RSAPrivateKeyFrom(priv10);
    var shortPlain10 = "RSA-short-text";
    var enc10 = CipherTools.RSAEncrypt(pubKey10, toUtf8(shortPlain10));
    var dec10 = CipherTools.RSADecrypt(privKey10, enc10);
    Logger.systemMark("TestCipherTools.js", "C10");
    check({
        caseId: "C10",
        name: "RSA keygen + public encrypt / private decrypt roundtrip",
        ok: pub10 !== null && priv10 !== null && enc10 !== null
            && fromUtf8(dec10) === shortPlain10,
        detail: "pubLen=" + (pub10 === null ? 0 : pub10.length)
            + " privLen=" + (priv10 === null ? 0 : priv10.length)
            + " encLen=" + (enc10 === null ? 0 : enc10.length),
        objective: "RSAPrivateKeyGen/RSAPublieKeyGen produce matching keys",
        objectiveZh: "RSA 金鑰產生器產生配對金鑰",
        command: "RSADecrypt(priv, RSAEncrypt(pub, bytes))"
    });

    // C11: RSA keys derived from the same seed are identical
    var priv11a = CipherTools.RSAPrivateKeyGen(seed10);
    var priv11b = CipherTools.RSAPrivateKeyGen(seed10);
    Logger.systemMark("TestCipherTools.js", "C11");
    check({
        caseId: "C11",
        name: "same RSA seed produces the same key",
        ok: bytesEqual(priv11a, priv11b),
        detail: "len=" + priv11a.length,
        objective: "deterministic SecureRandom seed -> reproducible keys",
        objectiveZh: "固定種子產生可重現金鑰",
        command: "RSAPrivateKeyGen(seed) == RSAPrivateKeyGen(seed)"
    });

} catch (e) {
    Logger.systemMark("TestCipherTools.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestCipherTools suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
