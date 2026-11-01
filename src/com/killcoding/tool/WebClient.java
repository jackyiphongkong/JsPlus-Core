/*******************************************************************************
The MIT License (MIT)

Copyright (c) 2024 KILLCODING.COM

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*****************************************************************************/

package com.killcoding.tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal synchronous HTTP client built on {@link HttpURLConnection},
 * supporting GET and POST over plain HTTP and HTTPS (using the JVM default
 * truststore and hostname verification unless explicitly disabled).
 * <p>
 * Known issues addressed:
 * <ul>
 *   <li><b>Bad certificates</b> - by default HTTPS is strict (system
 *       truststore + hostname check); for self-signed / expired / CN-mismatched
 *       servers (intranet, IP addresses) call {@link #setTrustAllCerts(boolean)}
 *       and/or {@link #setHostnameVerify(boolean)} before connecting. The
 *       trust-all option accepts ANY certificate, so it must only be used in
 *       controlled environments.</li>
 *   <li><b>gzip responses</b> - {@link #getResponseBytes()} /
 *       {@link #getErrorBytes()} transparently decompress bodies whose
 *       {@code Content-Encoding} is {@code gzip}, {@code x-gzip} or
 *       {@code deflate}; no explicit {@code Accept-Encoding} header is sent,
 *       but servers that compress unconditionally are handled correctly.</li>
 * </ul>
 * A request is executed with {@link #get()} or {@link #post()}, which open
 * the connection and return the HTTP status; the body is then read with
 * {@link #getResponseBytes()} or {@link #getErrorBytes()}. A client instance
 * holds one connection and is intended for a single request.
 */
public class WebClient {

	/** Set to {@code true} after {@link #connect()} when the target URL uses HTTPS. */
	protected boolean isHttps = false;
	/** The underlying connection created by {@link #connect()}. */
	protected HttpURLConnection conn;
	/** HTTP method to use, upper-cased; defaults to {@code "POST"}. */
	protected String method = "POST";
	/** Target URL (without any query string). */
	protected String url;
	/** Query string sent as the POST body or appended to a GET URL; may be {@code null}. */
	protected String qs;

	/**
	 * When {@code true}, accepts any server certificate chain (self-signed,
	 * expired, unknown CA). {@code false} (default) keeps the JVM system
	 * truststore strict validation.
	 */
	protected boolean trustAllCerts = false;

	/**
	 * When {@code true}, disables HTTPS hostname verification. {@code false}
	 * (default) keeps the JVM strict hostname check.
	 */
	protected boolean hostnameVerify = false;

	/** Hostname verifier that accepts every host (used only with {@link #hostnameVerify}). */
	private static final HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER = new HostnameVerifier() {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};

	/**
	 * Creates a client for the given URL with no query string.
	 *
	 * @param url target URL
	 */
	public WebClient(String url) {
		this.url = url;
	}
	
	/**
	 * Creates a client for the given URL with an explicit query string.
	 *
	 * @param url target URL
	 * @param qs  query string sent as the POST body or appended to GET
	 */
	public WebClient(String url, String qs) {
		this.url = url;
		this.qs = qs;
	}
	
	/**
	 * Executes a GET request.
	 *
	 * @return the HTTP status code
	 * @throws Exception when the connection cannot be opened
	 */
	public int get() throws Exception{
		setMethod("GET");
		return connect();
	}
	
	/**
	 * Executes a POST request, sending {@link #qs} as the body when set.
	 *
	 * @return the HTTP status code
	 * @throws Exception when the connection cannot be opened
	 */
	public int post() throws Exception{
		setMethod("POST");
		return connect();
	}

	/**
	 * Opens the connection for the configured {@link #method}, writing the query
	 * string as a POST body (or appending it to a GET URL), and returns the
	 * response code. HTTPS targets use the JVM default SSL context (strict
	 * truststore + hostname verification) unless {@link #setTrustAllCerts(boolean)}
	 * or {@link #setHostnameVerify(boolean)} was enabled first.
	 *
	 * @return the HTTP status code returned by the server
	 * @throws Exception when opening or connecting fails
	 */
	public int connect() throws Exception {
		URL connUrl = new URL(url);
		isHttps = "https".equalsIgnoreCase(connUrl.getProtocol());
		if (isHttps) {
			// Strict by default: system truststore + hostname verification. Callers
			// dealing with bad certificates (self-signed / expired / CN mismatch)
			// may opt into trust-all and/or relaxed hostname checks beforehand.
			HttpsURLConnection httpsConn = (HttpsURLConnection) connUrl.openConnection();
			conn = httpsConn;
			initConn();
			applySslOptions(httpsConn);
			httpsConn.setRequestMethod(method);
			httpsConn.setDoInput(true);
			httpsConn.setUseCaches(false);
			if (method.equals("POST")) {
				httpsConn.setDoOutput(true);
				if (qs != null) {
					writeBody(httpsConn, qs);
				}
			}
			conn.connect();
		} else {
			if (method.equals("POST")) {
				conn = (HttpURLConnection) connUrl.openConnection();
				initConn();
				conn.setRequestMethod(method);
				conn.setUseCaches(false);
				conn.setDoInput(true);
				conn.setDoOutput(true);
				if (qs != null) {
					writeBody(conn, qs);
				}
			} else if (method.equals("GET")) {
				connUrl = new URL(url
						+ (qs == null ? "" : "?" + qs));
				conn = (HttpURLConnection) connUrl.openConnection();
				initConn();
				conn.setRequestMethod(method);
				conn.setUseCaches(false);
				conn.setDoInput(true);
			}			
			conn.connect();
		}
		return conn.getResponseCode();
	}

	/**
	 * Applies the optional trust-all / relaxed-hostname settings to an HTTPS
	 * connection. Does nothing when both options are disabled (the default).
	 *
	 * @param httpsConn the HTTPS connection to configure
	 * @throws Exception when the trust-all SSL context cannot be built
	 */
	protected void applySslOptions(HttpsURLConnection httpsConn) throws Exception {
		if (trustAllCerts) {
			httpsConn.setSSLSocketFactory(buildTrustAllSslContext().getSocketFactory());
		}
		if (hostnameVerify) {
			httpsConn.setHostnameVerifier(ALLOW_ALL_HOSTNAME_VERIFIER);
		}
	}

	/**
	 * Builds an SSLContext that trusts every certificate chain without checking
	 * the CA or validity. Only used when {@link #trustAllCerts} is enabled; this
	 * must never be used against production servers.
	 *
	 * @return a trust-all SSL context
	 * @throws Exception when the TLS context cannot be created or initialised
	 */
	protected SSLContext buildTrustAllSslContext() throws Exception {
		TrustManager[] trustAll = new TrustManager[] {
			new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					// accept every client chain
				}
				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					// accept every server chain
				}
			}
		};
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, trustAll, new SecureRandom());
		return context;
	}

	/**
	 * Writes the POST body as UTF-8 bytes (avoids the platform-default charset)
	 * and closes the output stream.
	 *
	 * @param c    the open connection
	 * @param body the body text to send
	 * @throws IOException when writing fails
	 */
	protected void writeBody(HttpURLConnection c, String body) throws IOException {
		c.getOutputStream().write(body.getBytes("UTF-8"));
		c.getOutputStream().flush();
		c.getOutputStream().close();
	}
	
	/**
	 * Returns the underlying connection created by {@link #connect()}.
	 *
	 * @return the connection, or {@code null} before connecting
	 */
	public HttpURLConnection getConn(){
		return conn;
	}

	/**
	 * Sets the HTTP method, upper-cased.
	 *
	 * @param method {@code "GET"} or {@code "POST"}
	 */
	public void setMethod(String method) {
		this.method = method.toUpperCase();
	}
	
	/**
	 * Returns the configured HTTP method.
	 *
	 * @return the upper-cased method name
	 */
	public String getMethod(){
		return method;
	}

	/**
	 * Returns the target URL.
	 *
	 * @return the request URL
	 */
	public String getUrl(){
		return url;
	}

	/**
	 * Extracts the {@code charset} from the connection's {@code Content-Type}.
	 *
	 * @return the upper-cased charset, or {@code "UTF-8"} when none is declared
	 *         or there is no content type, or {@code null} before connecting
	 */
	public String getCharset() {
		if (conn == null) {
			return null;
		}
		String ct = conn.getContentType();
		if (ct != null) {
			Pattern pattern = Pattern
					.compile("(?<=(?i)(charset)=['\"]{0,1})([a-zA-Z0-9-/]+)(?=(['\"]{0,1}))");
			Matcher matcher = pattern.matcher(ct);
			return matcher.find() ? matcher.group().toUpperCase() : "UTF-8";
		} else {
			return "UTF-8";
		}
	}

	/**
	 * Sets the query string to send.
	 *
	 * @param qs query string (POST body or GET suffix)
	 */
	public void setQs(String qs) {
		this.qs = qs;
	}

	/**
	 * Returns the configured query string.
	 *
	 * @return the query string, or {@code null} when unset
	 */
	public String getQs() {
		return qs;
	}

	/**
	 * Enables or disables acceptance of any server certificate (self-signed,
	 * expired, unknown CA). Default is {@code false} = strict JVM truststore
	 * validation. Only enable for controlled intranet / test servers.
	 *
	 * @param trustAllCerts {@code true} to trust every certificate chain
	 */
	public void setTrustAllCerts(boolean trustAllCerts) {
		this.trustAllCerts = trustAllCerts;
	}

	/**
	 * Enables or disables HTTPS hostname verification. Default is {@code false}
	 * = strict JVM hostname check. Only disable when the server certificate
	 * hostname does not match the requested URL (e.g. IP-address targets).
	 *
	 * @param hostnameVerify {@code true} to skip hostname verification
	 */
	public void setHostnameVerify(boolean hostnameVerify) {
		this.hostnameVerify = hostnameVerify;
	}

	/**
	 * Reads the successful response body fully into a byte array, closing the
	 * stream in a {@code finally} block. When the response declares
	 * {@code Content-Encoding: gzip}/{@code x-gzip}/{@code deflate}, the body
	 * is transparently decompressed.
	 *
	 * @return the response bytes (decompressed when gzip/deflate-encoded)
	 * @throws IOException when reading the response stream fails
	 */
	public byte[] getResponseBytes() throws IOException {		
		InputStream input = null;
		ByteArrayOutputStream out = null;
		try {
			input = maybeWrapGzip(conn.getInputStream());
			out = new ByteArrayOutputStream(1024);
			byte[] buf = new byte[1024];
			int len = -1;
			while ((len = input.read(buf)) != -1) {
				out.write(buf, 0, len);
				out.flush();
			}
			return out.toByteArray();
		} finally {
			if (input != null)
				input.close();
			if (out != null)
				out.close();
		}
	}
	
	/**
	 * Reads the error stream (non-2xx response) fully into a byte array, closing
	 * the stream in a {@code finally} block. When the error response declares
	 * {@code Content-Encoding: gzip}/{@code x-gzip}/{@code deflate}, the body
	 * is transparently decompressed.
	 *
	 * @return the error body bytes (decompressed when gzip/deflate-encoded)
	 * @throws IOException when reading the error stream fails
	 */
	public byte[] getErrorBytes() throws IOException {		
		InputStream input = null;
		ByteArrayOutputStream out = null;
		try {
			input = maybeWrapGzip(conn.getErrorStream());
			out = new ByteArrayOutputStream(1024);
			byte[] buf = new byte[1024];
			int len = -1;
			while ((len = input.read(buf)) != -1) {
				out.write(buf, 0, len);
				out.flush();
			}
			return out.toByteArray();
		} finally {
			if (input != null)
				input.close();
			if (out != null)
				out.close();
		}
	}

	/**
	 * Wraps a raw response stream in a decompressor when the connection's
	 * {@code Content-Encoding} declares gzip/x-gzip/deflate. Servers that
	 * compress unconditionally are therefore handled correctly even though this
	 * client does not send an explicit {@code Accept-Encoding} header.
	 *
	 * @param input the raw response stream (may be {@code null})
	 * @return the possibly-wrapped stream, or {@code null} when {@code input} is null
	 * @throws IOException when the declared encoding is unsupported
	 */
	protected InputStream maybeWrapGzip(InputStream input) throws IOException {
		if (input == null) {
			return null;
		}
		String encoding = conn.getContentEncoding();
		if (encoding == null) {
			return input;
		}
		String enc = encoding.trim().toLowerCase();
		if (enc.equals("gzip") || enc.equals("x-gzip")) {
			return new GZIPInputStream(input);
		}
		if (enc.equals("deflate")) {
			// RFC 2616 defines HTTP "deflate" as the zlib-wrapped format.
			return new InflaterInputStream(input, new Inflater());
		}
		return input;
	}

	/**
	 * Reads the response body and decodes it with the connection charset.
	 *
	 * @return the decoded response text, or {@code null} when the body is empty
	 * @throws Exception when reading or decoding fails
	 */
	public String getResponseText() throws Exception {
		byte[] data = getResponseBytes();
		if (data != null)
			return new String(data, getCharset());
		else
			return null;
	}
	
	/**
	 * Reads the error body and decodes it with the connection charset.
	 *
	 * @return the decoded error text, or {@code null} when the body is empty
	 * @throws Exception when reading or decoding fails
	 */
	public String getErrorText() throws Exception {
		byte[] data = getErrorBytes();
		if (data != null)
			return new String(data, getCharset());
		else
			return null;
	}
	
	/** Default connect timeout (ms); 0 in HttpURLConnection means wait forever. */
	private int connectTimeoutMs = 10000;

	/** Default read timeout (ms); 0 in HttpURLConnection means wait forever. */
	private int readTimeoutMs = 30000;

	/** Applies the configured connect/read timeouts to the open connection. */
	protected void initConn(){
	    if (conn == null) return;
	    conn.setConnectTimeout(connectTimeoutMs);
	    conn.setReadTimeout(readTimeoutMs);
	}

	/**
	 * Sets the connect timeout.
	 *
	 * @param connectTimeoutMs timeout in milliseconds; {@code 0} means wait forever
	 */
	public void setConnectTimeoutMs(int connectTimeoutMs) {
	    this.connectTimeoutMs = connectTimeoutMs;
	}

	/**
	 * Sets the read timeout.
	 *
	 * @param readTimeoutMs timeout in milliseconds; {@code 0} means wait forever
	 */
	public void setReadTimeoutMs(int readTimeoutMs) {
	    this.readTimeoutMs = readTimeoutMs;
	}

	/**
	 * Returns the connect timeout.
	 *
	 * @return the connect timeout in milliseconds
	 */
	public int getConnectTimeoutMs() {
	    return connectTimeoutMs;
	}

	/**
	 * Returns the read timeout.
	 *
	 * @return the read timeout in milliseconds
	 */
	public int getReadTimeoutMs() {
	    return readTimeoutMs;
	}

}
