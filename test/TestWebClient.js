// ============================================================================
// JsPlus-Core TestWebClient - HTTP(S) client API suite
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestWebClient.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestWebClient-Report.html
//            (TestBase format, same as the other suites)
// Scope    : com.killcoding.tool.WebClient - synchronous HttpURLConnection
//            client. The suite compiles a small pure-Java test server
//            (WebTestServer, via Console.evalJavaSource) that serves plain and
//            gzip HTTP responses plus a self-signed HTTPS endpoint; the HTTPS
//            keystore is generated once into java.io.tmpdir with the JDK
//            keytool when missing. Covers: plain GET with charset fallback
//            (no Content-Type), POST body echo (UTF-8 Chinese), gzip response
//            decoding, error body (404), missing-Content-Type NPE guard,
//            strict HTTPS default (rejects the self-signed cert), and
//            trust-all + relaxed hostname verification (connects).
// NOTE     : Every java.lang.String crossing the GraalJS boundary is wrapped
//            with com.killcoding.jsplus.JsString (project convention); no regex
//            literals (GraalVM JS has no TRegex); server handlers run on the
//            server's own Java threads, so no JS callback crosses threads.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
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

var REPORT_TITLE = "JsPlus-Core WebClient Test";
var REPORT_NAME = "TestWebClient-Report.html";

beginTest();

print("=== JsPlus-Core WebClient Test ===");
print("JS_PLUS_PATH: " + basePath);

// Unwrap a Java String / JsString / JS value to a plain JS string.
function str(s) {
    return JsString.valueOf(s).asString();
}

// Pure-Java test server (EOF template literal - no string concatenation).
// Handlers run on the server's own threads; the suite only talks to it via
// WebClient, which is exactly what is under test.
var SRC_SERVER = `
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * Test-only embedded HTTP/HTTPS server for the TestWebClient suite.
 * Serves on auto-assigned ports (0):
 *   GET  /plain    200 "Hello World"                (no Content-Type -> charset fallback)
 *   POST /echo     echoes the request body verbatim (UTF-8)
 *   GET  /gzip     200 gzip-compressed body         (Content-Encoding: gzip)
 *   GET  /notfound 404 "not found body"             (error stream)
 *   GET  /no-ctype 200 body with no Content-Type    (NPE guard)
 *   GET  /secure   HTTPS only: 200 "secure ok"      (self-signed cert)
 * The self-signed PKCS12 keystore is generated once into java.io.tmpdir via
 * the JDK keytool when missing; the HTTPS listener starts only on success.
 */
public class WebTestServer {
    private final HttpServer http;
    private HttpsServer https;
    private final int httpPort;
    private final int httpsPort;
    private final String keystorePath;

    public WebTestServer() throws Exception {
        String tmp = System.getProperty("java.io.tmpdir");
        keystorePath = tmp + (tmp.endsWith(File.separator) ? "" : File.separator)
                + "jsplus-webclient-test.p12";
        ensureKeystore();

        http = HttpServer.create(new InetSocketAddress(0), 0);
        http.createContext("/plain", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                respond(ex, 200, "Hello World".getBytes("UTF-8"), null);
            }
        });
        http.createContext("/echo", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                byte[] body = readAll(ex.getRequestBody());
                respond(ex, 200, body, "text/plain; charset=UTF-8");
            }
        });
        http.createContext("/gzip", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                String text = "gzip response body 中文 1234567890";
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gz = new GZIPOutputStream(baos);
                gz.write(text.getBytes("UTF-8"));
                gz.close();
                ex.getResponseHeaders().set("Content-Encoding", "gzip");
                respond(ex, 200, baos.toByteArray(), "text/plain; charset=UTF-8");
            }
        });
        http.createContext("/notfound", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                respond(ex, 404, "not found body".getBytes("UTF-8"), "text/plain");
            }
        });
        http.createContext("/no-ctype", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                byte[] body = "no content type body".getBytes("UTF-8");
                ex.sendResponseHeaders(200, body.length);
                OutputStream os = ex.getResponseBody();
                os.write(body);
                os.close();
            }
        });
        http.start();
        httpPort = http.getAddress().getPort();

        // HTTPS listener: only when the keystore exists.
        if (new File(keystorePath).exists()) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, "changeit".toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "changeit".toCharArray());
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);

            https = HttpsServer.create(new InetSocketAddress(0), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(sc));
            https.createContext("/secure", new HttpHandler() {
                public void handle(HttpExchange ex) throws IOException {
                    respond(ex, 200, "secure ok".getBytes("UTF-8"), "text/plain");
                }
            });
            https.start();
            httpsPort = https.getAddress().getPort();
        } else {
            httpsPort = -1;
        }
    }

    public int getHttpPort() { return httpPort; }
    public int getHttpsPort() { return httpsPort; }
    public boolean isHttpsAvailable() { return httpsPort > 0; }

    public void stop() {
        http.stop(0);
        if (https != null) {
            https.stop(0);
        }
    }

    private static void respond(HttpExchange ex, int status, byte[] body, String contentType) throws IOException {
        if (contentType != null) {
            ex.getResponseHeaders().set("Content-Type", contentType);
        }
        ex.sendResponseHeaders(status, body.length);
        OutputStream os = ex.getResponseBody();
        os.write(body);
        os.close();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private void ensureKeystore() throws Exception {
        File kf = new File(keystorePath);
        if (kf.exists()) {
            return;
        }
        String exe = isWindows() ? "keytool.exe" : "keytool";
        String jh = System.getProperty("java.home");
        File keytool = new File(jh + File.separator + "bin" + File.separator + exe);
        if (!keytool.exists()) {
            File parent = new File(jh).getParentFile();
            if (parent != null) {
                File alt = new File(parent, "bin" + File.separator + exe);
                if (alt.exists()) {
                    keytool = alt;
                }
            }
        }
        if (!keytool.exists()) {
            System.err.println("[WebTestServer] keytool not found - HTTPS cases will be skipped");
            return;
        }
        Process p = new ProcessBuilder(keytool.getAbsolutePath(),
                "-genkeypair", "-alias", "webclienttest", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3650", "-dname", "CN=localhost, OU=killcoding-test, O=killcoding, C=HK",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keystorePath,
                "-storepass", "changeit", "-keypass", "changeit", "-noprompt")
                .redirectErrorStream(true).start();
        p.waitFor();
        if (!kf.exists()) {
            System.err.println("[WebTestServer] keytool failed - HTTPS cases will be skipped");
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().indexOf("win") >= 0;
    }
}`;

try {
    var WebClient = Java.type("com.killcoding.tool.WebClient");

    // Build the test server (dynamic Java compile; handlers stay on Java threads).
    var loader = Console.evalJavaSource(SRC_SERVER);
    var ServerClass = loader.loadClass("WebTestServer");
    var server = ServerClass.newInstance();
    var httpPort = server.getHttpPort();
    var httpsPort = server.getHttpsPort();
    var httpsAvailable = server.isHttpsAvailable();
    print("[TestWebClient] http port=" + httpPort + " https port=" + httpsPort
        + " httpsAvailable=" + httpsAvailable);
    var base = "http://localhost:" + httpPort;

    // C1: plain GET - body decodes via the UTF-8 fallback (no Content-Type charset)
    var c1 = new WebClient(base + "/plain");
    var c1code = c1.get();
    var c1body = str(c1.getResponseText());
    Logger.systemMark("TestWebClient.js", "C1");
    check({
        caseId: "C1",
        name: "plain GET returns the body (charset fallback without Content-Type)",
        ok: c1code === 200 && c1body === "Hello World",
        detail: "code=" + c1code + " body=" + c1body,
        objective: "get()/getResponseText() decode with UTF-8 when no Content-Type charset is declared",
        objectiveZh: "無 Content-Type 字元集時 get()/getResponseText() 以 UTF-8 回退解碼",
        command: "new WebClient(base + '/plain').get() + getResponseText()"
    });

    // C2: POST echo - UTF-8 Chinese body survives the round trip
    var c2 = new WebClient(base + "/echo", "name=張三&note=測試");
    var c2code = c2.post();
    var c2body = str(c2.getResponseText());
    Logger.systemMark("TestWebClient.js", "C2");
    check({
        caseId: "C2",
        name: "POST body is echoed (UTF-8 Chinese survives)",
        ok: c2code === 200 && c2body === "name=張三&note=測試",
        detail: "code=" + c2code + " body=" + c2body,
        objective: "POST writes the query string as UTF-8 bytes and the server echoes it",
        objectiveZh: "POST 以 UTF-8 位元組寫入 query string，伺服器原樣回顯",
        command: "new WebClient(base + '/echo', qs).post() + getResponseText()"
    });

    // C3: gzip response - transparently decompressed
    var c3 = new WebClient(base + "/gzip");
    var c3code = c3.get();
    var c3body = str(c3.getResponseText());
    Logger.systemMark("TestWebClient.js", "C3");
    check({
        caseId: "C3",
        name: "gzip response is transparently decompressed",
        ok: c3code === 200 && c3body === "gzip response body 中文 1234567890",
        detail: "code=" + c3code + " body=" + c3body,
        objective: "getResponseBytes()/getResponseText() unwrap Content-Encoding: gzip",
        objectiveZh: "getResponseBytes()/getResponseText() 自動解開 Content-Encoding: gzip",
        command: "new WebClient(base + '/gzip').get() + getResponseText()"
    });

    // C4: 404 - error body readable via getErrorBytes()/getErrorText()
    var c4 = new WebClient(base + "/notfound");
    var c4code = c4.get();
    var c4err = str(c4.getErrorText());
    Logger.systemMark("TestWebClient.js", "C4");
    check({
        caseId: "C4",
        name: "404 error body is readable",
        ok: c4code === 404 && c4err === "not found body",
        detail: "code=" + c4code + " err=" + c4err,
        objective: "getErrorBytes()/getErrorText() read the non-2xx response body",
        objectiveZh: "getErrorBytes()/getErrorText() 讀取非 2xx 響應體",
        command: "new WebClient(base + '/notfound').get() + getErrorText()"
    });

    // C5: no Content-Type at all - getResponseText() must not NPE (UTF-8 fallback)
    var c5 = new WebClient(base + "/no-ctype");
    var c5code = c5.get();
    var c5body = str(c5.getResponseText());
    Logger.systemMark("TestWebClient.js", "C5");
    check({
        caseId: "C5",
        name: "missing Content-Type does not NPE (charset falls back to UTF-8)",
        ok: c5code === 200 && c5body === "no content type body",
        detail: "code=" + c5code + " body=" + c5body,
        objective: "getCharset() returns UTF-8 when the response has no Content-Type",
        objectiveZh: "響應無 Content-Type 時 getCharset() 回退 UTF-8，getResponseText() 不拋 NPE",
        command: "new WebClient(base + '/no-ctype').get() + getResponseText()"
    });

    if (httpsAvailable) {
        // C6: strict default - the self-signed cert must be rejected
        var c6 = new WebClient("https://localhost:" + httpsPort + "/secure");
        var c6threw = false;
        var c6err = "";
        try {
            c6.get();
        } catch (e) {
            c6threw = true;
            c6err = str(e.toString());
        }
        Logger.systemMark("TestWebClient.js", "C6");
        check({
            caseId: "C6",
            name: "strict default rejects the self-signed certificate",
            ok: c6threw,
            detail: c6threw ? "threw: " + c6err.substring(0, 120) : "unexpectedly connected",
            objective: "without trustAllCerts the JVM truststore rejects a self-signed server cert",
            objectiveZh: "未開啟 trustAllCerts 時 JVM truststore 拒絕自簽名伺服器證書",
            command: "new WebClient(httpsUrl).get() (default settings)"
        });

        // C7: trust-all + relaxed hostname - connects and returns the body
        var c7 = new WebClient("https://localhost:" + httpsPort + "/secure");
        c7.setTrustAllCerts(true);
        c7.setHostnameVerify(true);
        var c7code = c7.get();
        var c7body = str(c7.getResponseText());
        Logger.systemMark("TestWebClient.js", "C7");
        check({
            caseId: "C7",
            name: "trust-all + relaxed hostname connects to the self-signed server",
            ok: c7code === 200 && c7body === "secure ok",
            detail: "code=" + c7code + " body=" + c7body,
            objective: "setTrustAllCerts(true)+setHostnameVerify(true) bypass bad-cert checks",
            objectiveZh: "setTrustAllCerts(true)+setHostnameVerify(true) 繞過爛證書檢查成功連線",
            command: "new WebClient(httpsUrl) trustAllCerts+hostnameVerify .get() + getResponseText()"
        });
    } else {
        Logger.systemMark("TestWebClient.js", "C6");
        check({
            caseId: "C6",
            name: "HTTPS cases require keytool (self-signed keystore)",
            ok: false,
            detail: "keytool not found - cannot start the HTTPS listener",
            objective: "HTTPS listener needs a self-signed PKCS12 keystore",
            objectiveZh: "HTTPS 監聽需要自簽名 PKCS12 keystore（keytool 缺失）"
        });
        Logger.systemMark("TestWebClient.js", "C7");
        check({
            caseId: "C7",
            name: "HTTPS cases require keytool (self-signed keystore)",
            ok: false,
            detail: "keytool not found - cannot start the HTTPS listener",
            objective: "HTTPS listener needs a self-signed PKCS12 keystore",
            objectiveZh: "HTTPS 監聽需要自簽名 PKCS12 keystore（keytool 缺失）"
        });
    }

    server.stop();

} catch (e) {
    Logger.systemMark("TestWebClient.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestWebClient suite error",
        ok: false,
        detail: str(e.toString())
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
