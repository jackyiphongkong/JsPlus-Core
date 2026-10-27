/* ************************************************************************
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

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;

/**
* Internal escape / unescape engine (package-private): character escape tables, SEC mapping and
* stream-based processing used by CodeEscape and EscapeTools.
* */
final class CodeEscapeUtil {


    private static final char ESCAPE_PREFIX = '\\';
    private static final char ESCAPE_UHEXA_PREFIX2 = 'u';
    private static final char[] ESCAPE_UHEXA_PREFIX = "\\u".toCharArray();

    private static char[] HEXA_CHARS_UPPER = "0123456789ABCDEF".toCharArray();
    private static char[] HEXA_CHARS_LOWER = "0123456789abcdef".toCharArray();

    private static int SEC_CHARS_LEN = '\\' + 1;
    private static char SEC_CHARS_NO_SEC = '*';
    private static char[] SEC_CHARS;

    private static final char ESCAPE_LEVELS_LEN = 0x9f + 2;
    private static final byte[] ESCAPE_LEVELS;



    static {

        SEC_CHARS = new char[SEC_CHARS_LEN];
        Arrays.fill(SEC_CHARS,SEC_CHARS_NO_SEC);
        SEC_CHARS[0x08] = 'b';
        SEC_CHARS[0x09] = 't';
        SEC_CHARS[0x0A] = 'n';
        SEC_CHARS[0x0C] = 'f';
        SEC_CHARS[0x0D] = 'r';
        SEC_CHARS[0x22] = '"';

        SEC_CHARS[0x27] = '\'';
        SEC_CHARS[0x5C] = '\\';

        ESCAPE_LEVELS = new byte[ESCAPE_LEVELS_LEN];

        Arrays.fill(ESCAPE_LEVELS, (byte)3);

        for (char c = 0x80; c < ESCAPE_LEVELS_LEN; c++) {
            ESCAPE_LEVELS[c] = 2;
        }

        for (char c = 'A'; c <= 'Z'; c++) {
            ESCAPE_LEVELS[c] = 4;
        }
        for (char c = 'a'; c <= 'z'; c++) {
            ESCAPE_LEVELS[c] = 4;
        }
        for (char c = '0'; c <= '9'; c++) {
            ESCAPE_LEVELS[c] = 4;
        }

        ESCAPE_LEVELS[0x08] = 1;
        ESCAPE_LEVELS[0x09] = 1;
        ESCAPE_LEVELS[0x0A] = 1;
        ESCAPE_LEVELS[0x0C] = 1;
        ESCAPE_LEVELS[0x0D] = 1;
        ESCAPE_LEVELS[0x22] = 1;

        ESCAPE_LEVELS[0x27] = 3;
        ESCAPE_LEVELS[0x5C] = 1;

        for (char c = 0x00; c <= 0x1F; c++) {
            ESCAPE_LEVELS[c] = 1;
        }
        for (char c = 0x7F; c <= 0x9F; c++) {
            ESCAPE_LEVELS[c] = 1;
        }

    }



    /**
    * Constructs a new CodeEscapeUtil
    * */
    private CodeEscapeUtil() {
        super();
    }

    /**
    * Performs the toUHexa operation
    * @param codepoint - the codepoint
    * @return the toUHexa result
    * */
    static char[] toUHexa(final int codepoint) {
        final char[] result = new char[4];
        result[3] = HEXA_CHARS_UPPER[codepoint % 0x10];
        result[2] = HEXA_CHARS_UPPER[(codepoint >>> 4) % 0x10];
        result[1] = HEXA_CHARS_UPPER[(codepoint >>> 8) % 0x10];
        result[0] = HEXA_CHARS_UPPER[(codepoint >>> 12) % 0x10];
        return result;
    }

    /**
    * Performs the escape operation
    * @param text - the text
    * @param escapeLevel - the escapeLevel
    * @return the escape result
    * */
    static String escape(final String text, final CodeEscapeLevel escapeLevel) {

        if (text == null) {
            return null;
        }

        final int level = escapeLevel.getEscapeLevel();

        StringBuilder strBuilder = null;

        final int offset = 0;
        final int max = text.length();

        int readOffset = offset;

        for (int i = offset; i < max; i++) {

            final int codepoint = Character.codePointAt(text, i);

            if (codepoint <= (ESCAPE_LEVELS_LEN - 2) && level < ESCAPE_LEVELS[codepoint]) {
                continue;
            }

            if (codepoint > (ESCAPE_LEVELS_LEN - 2) && level < ESCAPE_LEVELS[ESCAPE_LEVELS_LEN - 1]) {

                if (Character.charCount(codepoint) > 1) {
                    i++;
                }

                continue;

            }

            if (strBuilder == null) {
                strBuilder = new StringBuilder(max + 20);
            }

            if (i - readOffset > 0) {
                strBuilder.append(text, readOffset, i);
            }

            if (Character.charCount(codepoint) > 1) {
                i++;
            }

            readOffset = i + 1;




            if (codepoint < SEC_CHARS_LEN) {

                final char sec = SEC_CHARS[codepoint];

                if (sec != SEC_CHARS_NO_SEC) {

                    strBuilder.append(ESCAPE_PREFIX);
                    strBuilder.append(sec);
                    continue;
                }

            }

            if (Character.charCount(codepoint) > 1) {
                final char[] codepointChars = Character.toChars(codepoint);
                strBuilder.append(ESCAPE_UHEXA_PREFIX);
                strBuilder.append(toUHexa(codepointChars[0]));
                strBuilder.append(ESCAPE_UHEXA_PREFIX);
                strBuilder.append(toUHexa(codepointChars[1]));
                continue;
            }

            strBuilder.append(ESCAPE_UHEXA_PREFIX);
            strBuilder.append(toUHexa(codepoint));

        }

        if (strBuilder == null) {
            return text;
        }

        if (max - readOffset > 0) {
            strBuilder.append(text, readOffset, max);
        }

        return strBuilder.toString();

    }

    /**
    * Performs the escape operation
    * @param reader - the reader
    * @param writer - the writer
    * @param escapeLevel - the escapeLevel
    * */
    static void escape(final Reader reader, final Writer writer, final CodeEscapeLevel escapeLevel)
            throws IOException {

        if (reader == null) {
            return;
        }

        final int level = escapeLevel.getEscapeLevel();

        int c1, c2;

        c2 = reader.read();

        while (c2 >= 0) {

            c1 = c2;
            c2 = reader.read();

            final int codepoint = codePointAt((char)c1, (char)c2);

            if (codepoint <= (ESCAPE_LEVELS_LEN - 2) && level < ESCAPE_LEVELS[codepoint]) {
                writer.write(c1);
                continue;
            }

            if (codepoint > (ESCAPE_LEVELS_LEN - 2) && level < ESCAPE_LEVELS[ESCAPE_LEVELS_LEN - 1]) {

                writer.write(c1);

                if (Character.charCount(codepoint) > 1) {
                    writer.write(c2);

                    c1 = c2;
                    c2 = reader.read();

                }

                continue;

            }


            if (Character.charCount(codepoint) > 1) {
                c1 = c2;
                c2 = reader.read();
            }

            if (codepoint < SEC_CHARS_LEN) {

                final char sec = SEC_CHARS[codepoint];

                if (sec != SEC_CHARS_NO_SEC) {
                    writer.write(ESCAPE_PREFIX);
                    writer.write(sec);
                    continue;
                }

            }

            if (Character.charCount(codepoint) > 1) {
                final char[] codepointChars = Character.toChars(codepoint);
                writer.write(ESCAPE_UHEXA_PREFIX);
                writer.write(toUHexa(codepointChars[0]));
                writer.write(ESCAPE_UHEXA_PREFIX);
                writer.write(toUHexa(codepointChars[1]));
                continue;
            }

            writer.write(ESCAPE_UHEXA_PREFIX);
            writer.write(toUHexa(codepoint));

        }

    }

    /**
    * Performs the escape operation
    * @param text - the text
    * @param offset - the offset
    * @param len - the len
    * @param writer - the writer
    * @param escapeLevel - the escapeLevel
    * */
    static void escape(final char[] text, final int offset, final int len, final Writer writer,
                       final CodeEscapeLevel escapeLevel)
                       throws IOException {

        if (text == null || text.length == 0) {
            return;
        }

        final int level = escapeLevel.getEscapeLevel();

        final int max = (offset + len);

        int readOffset = offset;

        for (int i = offset; i < max; i++) {

            final int codepoint = Character.codePointAt(text, i);

            if (codepoint <= (ESCAPE_LEVELS_LEN - 2) && level < ESCAPE_LEVELS[codepoint]) {
                continue;
            }

            if (codepoint > (ESCAPE_LEVELS_LEN - 2) && level < ESCAPE_LEVELS[ESCAPE_LEVELS_LEN - 1]) {

                if (Character.charCount(codepoint) > 1) {
                    i++;
                }

                continue;

            }

            if (i - readOffset > 0) {
                writer.write(text, readOffset, (i - readOffset));
            }

            if (Character.charCount(codepoint) > 1) {
                i++;
            }

            readOffset = i + 1;

            if (codepoint < SEC_CHARS_LEN) {

                final char sec = SEC_CHARS[codepoint];

                if (sec != SEC_CHARS_NO_SEC) {
                    writer.write(ESCAPE_PREFIX);
                    writer.write(sec);
                    continue;
                }

            }

            if (Character.charCount(codepoint) > 1) {
                final char[] codepointChars = Character.toChars(codepoint);
                writer.write(ESCAPE_UHEXA_PREFIX);
                writer.write(toUHexa(codepointChars[0]));
                writer.write(ESCAPE_UHEXA_PREFIX);
                writer.write(toUHexa(codepointChars[1]));
                continue;
            }

            writer.write(ESCAPE_UHEXA_PREFIX);
            writer.write(toUHexa(codepoint));

        }

        if (max - readOffset > 0) {
            writer.write(text, readOffset, (max - readOffset));
        }

    }
    
    /**
    * Performs the parseIntFromReference operation
    * @param text - the text
    * @param start - the start
    * @param end - the end
    * @param radix - the radix
    * @return the parseIntFromReference result
    * */
    static int parseIntFromReference(final String text, final int start, final int end, final int radix) {
        int result = 0;
        for (int i = start; i < end; i++) {
            final char c = text.charAt(i);
            int n = -1;
            for (int j = 0; j < HEXA_CHARS_UPPER.length; j++) {
                if (c == HEXA_CHARS_UPPER[j] || c == HEXA_CHARS_LOWER[j]) {
                    n = j;
                    break;
                }
            }
            result = (radix * result) + n;
        }
        return result;
    }

    /**
    * Performs the parseIntFromReference operation
    * @param text - the text
    * @param start - the start
    * @param end - the end
    * @param radix - the radix
    * @return the parseIntFromReference result
    * */
    static int parseIntFromReference(final char[] text, final int start, final int end, final int radix) {
        int result = 0;
        for (int i = start; i < end; i++) {
            final char c = text[i];
            int n = -1;
            for (int j = 0; j < HEXA_CHARS_UPPER.length; j++) {
                if (c == HEXA_CHARS_UPPER[j] || c == HEXA_CHARS_LOWER[j]) {
                    n = j;
                    break;
                }
            }
            result = (radix * result) + n;
        }
        return result;
    }

    /**
    * Performs the isOctalEscape operation
    * @param text - the text
    * @param start - the start
    * @param end - the end
    * @return the isOctalEscape result
    * */
    static boolean isOctalEscape(final String text, final int start, final int end) {

        if (start >= end) {
            return false;
        }

        final char c1 = text.charAt(start);
        if (c1 < '0' || c1 > '7') {
            return false;
        }

        if (start + 1 >= end) {
            return (c1 != '0'); 
        }

        final char c2 = text.charAt(start + 1);
        if (c2 < '0' || c2 > '7') {
            return (c1 != '0');
        }

        if (start + 2 >= end) {
            return (c1 != '0' || c2 != '0'); 
        }

        final char c3 = text.charAt(start + 2);
        if (c3 < '0' || c3 > '7') {
            return (c1 != '0' || c2 != '0');
        }

        return (c1 != '0' || c2 != '0' || c3 != '0'); 

    }


    /**
    * Performs the isOctalEscape operation
    * @param text - the text
    * @param start - the start
    * @param end - the end
    * @return the isOctalEscape result
    * */
    static boolean isOctalEscape(final char[] text, final int start, final int end) {

        if (start >= end) {
            return false;
        }

        final char c1 = text[start];
        if (c1 < '0' || c1 > '7') {
            return false;
        }

        if (start + 1 >= end) {
            return (c1 != '0');
        }

        final char c2 = text[start + 1];
        if (c2 < '0' || c2 > '7') {
            return (c1 != '0'); 
        }

        if (start + 2 >= end) {
            return (c1 != '0' || c2 != '0'); 
        }

        final char c3 = text[start + 2];
        if (c3 < '0' || c3 > '7') {
            return (c1 != '0' || c2 != '0'); 
        }

        return (c1 != '0' || c2 != '0' || c3 != '0'); 

    }

    /**
    * Performs the unicodeUnescape operation
    * @param text - the text
    * @return the unicodeUnescape result
    * */
    static String unicodeUnescape(final String text) {

        if (text == null) {
            return null;
        }

        StringBuilder strBuilder = null;

        final int offset = 0;
        final int max = text.length();

        int readOffset = offset;
        int referenceOffset = offset;

        for (int i = offset; i < max; i++) {

            final char c = text.charAt(i);


            if (c != ESCAPE_PREFIX || (i + 1) >= max) {
                continue;
            }

            int codepoint = -1;

            if (c == ESCAPE_PREFIX) {

                final char c1 = text.charAt(i + 1);

                if (c1 == ESCAPE_UHEXA_PREFIX2) {

                    int f = i + 2;

                    while (f < max) {
                        final char cf = text.charAt(f);
                        if (cf != ESCAPE_UHEXA_PREFIX2) {
                            break;
                        }
                        f++;
                    }
                    int s = f;

                    while (f < (s + 4) && f < max) {
                        final char cf = text.charAt(f);
                        if (!((cf >= '0' && cf <= '9') || (cf >= 'A' && cf <= 'F') || (cf >= 'a' && cf <= 'f'))) {
                            break;
                        }
                        f++;
                    }

                    if ((f - s) < 4) {
                        i++;
                        continue;
                    }

                    codepoint = parseIntFromReference(text, s, f, 16);

                    referenceOffset = f - 1;

                } else {

                    i++;
                    continue;

                }

            }

            if (strBuilder == null) {
                strBuilder = new StringBuilder(max + 5);
            }

            if (i - readOffset > 0) {
                strBuilder.append(text, readOffset, i);
            }

            i = referenceOffset;
            readOffset = i + 1;

            if (codepoint > '\uFFFF') {
                strBuilder.append(Character.toChars(codepoint));
            } else {
                strBuilder.append((char)codepoint);
            }

        }


        if (strBuilder == null) {
            return text;
        }

        if (max - readOffset > 0) {
            strBuilder.append(text, readOffset, max);
        }

        return strBuilder.toString();

    }



    /**
    * Performs the requiresUnicodeUnescape operation
    * @param text - the text
    * @param offset - the offset
    * @param len - the len
    * @return the requiresUnicodeUnescape result
    * */
    static boolean requiresUnicodeUnescape(final char[] text, final int offset, final int len) {

        if (text == null) {
            return false;
        }

        final int max = (offset + len);

        for (int i = offset; i < max; i++) {

            final char c = text[i];

            if (c != ESCAPE_PREFIX || (i + 1) >= max) {
                continue;
            }

            if (c == ESCAPE_PREFIX) {

                final char c1 = text[i + 1];

                if (c1 == ESCAPE_UHEXA_PREFIX2) {
                    return true;
                }

            }

        }

        return false;

    }



    /**
    * Performs the unicodeUnescape operation
    * @param text - the text
    * @param offset - the offset
    * @param len - the len
    * @param writer - the writer
    * */
    static void unicodeUnescape(final char[] text, final int offset, final int len, final Writer writer)
                                throws IOException {

        if (text == null) {
            return;
        }

        final int max = (offset + len);

        int readOffset = offset;
        int referenceOffset = offset;

        for (int i = offset; i < max; i++) {

            final char c = text[i];

            if (c != ESCAPE_PREFIX || (i + 1) >= max) {
                continue;
            }

            int codepoint = -1;

            if (c == ESCAPE_PREFIX) {

                final char c1 = text[i + 1];

                if (c1 == ESCAPE_UHEXA_PREFIX2) {

                    int f = i + 2;

                    while (f < max) {
                        final char cf = text[f];
                        if (cf != ESCAPE_UHEXA_PREFIX2) {
                            break;
                        }
                        f++;
                    }
                    int s = f;

                    while (f < (s + 4) && f < max) {
                        final char cf = text[f];
                        if (!((cf >= '0' && cf <= '9') || (cf >= 'A' && cf <= 'F') || (cf >= 'a' && cf <= 'f'))) {
                            break;
                        }
                        f++;
                    }

                    if ((f - s) < 4) {
                        i++;
                        continue;
                    }

                    codepoint = parseIntFromReference(text, s, f, 16);

                    referenceOffset = f - 1;


                } else {
                    i++;
                    continue;

                }

            }

            if (i - readOffset > 0) {
                writer.write(text, readOffset, (i - readOffset));
            }

            i = referenceOffset;
            readOffset = i + 1;

            if (codepoint > '\uFFFF') {
                writer.write(Character.toChars(codepoint));
            } else {
                writer.write((char) codepoint);
            }

        }


        if (max - readOffset > 0) {
            writer.write(text, readOffset, (max - readOffset));
        }

    }

    /**
    * Performs the unescape operation
    * @param text - the text
    * @return the unescape result
    * */
    static String unescape(final String text) {

        if (text == null) {
            return null;
        }

        final String unicodeEscapedText = unicodeUnescape(text);

        StringBuilder strBuilder = null;

        final int offset = 0;
        final int max = unicodeEscapedText.length();

        int readOffset = offset;
        int referenceOffset = offset;

        for (int i = offset; i < max; i++) {

            final char c = unicodeEscapedText.charAt(i);

            if (c != ESCAPE_PREFIX || (i + 1) >= max) {
                continue;
            }

            int codepoint = -1;

            if (c == ESCAPE_PREFIX) {

                final char c1 = unicodeEscapedText.charAt(i + 1);

                switch (c1) {
                    case '0': if (!isOctalEscape(unicodeEscapedText,i + 1,max)) { codepoint = 0x00; referenceOffset = i + 1; }; break;
                    case 'b': codepoint = 0x08; referenceOffset = i + 1; break;
                    case 't': codepoint = 0x09; referenceOffset = i + 1; break;
                    case 'n': codepoint = 0x0A; referenceOffset = i + 1; break;
                    case 'f': codepoint = 0x0C; referenceOffset = i + 1; break;
                    case 'r': codepoint = 0x0D; referenceOffset = i + 1; break;
                    case '"': codepoint = 0x22; referenceOffset = i + 1; break;
                    case '\'': codepoint = 0x27; referenceOffset = i + 1; break;
                    case '\\': codepoint = 0x5C; referenceOffset = i + 1; break;
                }

                if (codepoint == -1) {

                    if (c1 >= '0' && c1 <= '7') {

                        int f = i + 2;
                        while (f < (i + 4) && f < max) {
                            final char cf = unicodeEscapedText.charAt(f);
                            if (!(cf >= '0' && cf <= '7')) {
                                break;
                            }
                            f++;
                        }

                        codepoint = parseIntFromReference(unicodeEscapedText, i + 1, f, 8);

                        if (codepoint > 0xFF) {
                            codepoint = parseIntFromReference(unicodeEscapedText, i + 1, f - 1, 8);
                            referenceOffset = f - 2;
                        } else {
                            referenceOffset = f - 1;
                        }

                    } else {
                        i++;
                        continue;

                    }

                }

            }
            
            if (strBuilder == null) {
                strBuilder = new StringBuilder(max + 5);
            }

            if (i - readOffset > 0) {
                strBuilder.append(unicodeEscapedText, readOffset, i);
            }

            i = referenceOffset;
            readOffset = i + 1;

            if (codepoint > '\uFFFF') {
                strBuilder.append(Character.toChars(codepoint));
            } else {
                strBuilder.append((char)codepoint);
            }

        }

        if (strBuilder == null) {
            return unicodeEscapedText;
        }

        if (max - readOffset > 0) {
            strBuilder.append(unicodeEscapedText, readOffset, max);
        }

        return strBuilder.toString();

    }

    /**
    * Performs the unescape operation
    * @param reader - the reader
    * @param writer - the writer
    * */
    static void unescape(final Reader reader, final Writer writer) throws IOException {

        if (reader == null) {
            return;
        }
        
        char[] buffer = new char[20];

        int read = reader.read(buffer, 0, buffer.length);
        if (read < 0) {
            return;
        }

        int bufferSize = read;

        while (bufferSize > 0 || read >= 0) {

            int nonEscCounter = 0;

            int n = bufferSize;
            while (nonEscCounter < 8 && n-- != 0) {
                if (buffer[n] == '\\') {
                    nonEscCounter = 0;
                    continue;
                }
                nonEscCounter++;
            }

            if (nonEscCounter < 8 && read >= 0) {

                if (bufferSize == buffer.length) {
                    final char[] newBuffer = new char[buffer.length + (buffer.length / 2)];
                    System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                    buffer = newBuffer;
                }

                read = reader.read(buffer, bufferSize, (buffer.length - bufferSize));
                if (read >= 0) {
                    bufferSize += read;
                }

                continue;

            }

            n = (n < 0? bufferSize : n + nonEscCounter);

            unescape(buffer, 0, n, writer);

            System.arraycopy(buffer, n, buffer, 0, (bufferSize - n));
            bufferSize -= n;

            read = reader.read(buffer, bufferSize, (buffer.length - bufferSize));
            if (read >= 0) {
                bufferSize += read;
            }

        }

    }

    /**
    * Performs the unescape operation
    * @param text - the text
    * @param offset - the offset
    * @param len - the len
    * @param writer - the writer
    * */
    static void unescape(final char[] text, final int offset, final int len, final Writer writer)
                         throws IOException {

        if (text == null) {
            return;
        }

        char[] unicodeEscapedText = text;
        int unicodeEscapedOffset = offset;
        int unicodeEscapedLen = len;
        if (requiresUnicodeUnescape(text, offset, len)) {
            final CharArrayWriter charArrayWriter = new CharArrayWriter(len + 2);
            unicodeUnescape(text, offset, len, charArrayWriter);
            unicodeEscapedText = charArrayWriter.toCharArray();
            unicodeEscapedOffset = 0;
            unicodeEscapedLen = unicodeEscapedText.length;
        }

        final int max = (unicodeEscapedOffset + unicodeEscapedLen);

        int readOffset = unicodeEscapedOffset;
        int referenceOffset = unicodeEscapedOffset;

        for (int i = unicodeEscapedOffset; i < max; i++) {

            final char c = unicodeEscapedText[i];

            if (c != ESCAPE_PREFIX || (i + 1) >= max) {
                continue;
            }

            int codepoint = -1;

            if (c == ESCAPE_PREFIX) {

                final char c1 = unicodeEscapedText[i + 1];

                switch (c1) {
                    case '0': if (!isOctalEscape(unicodeEscapedText,i + 1,max)) { codepoint = 0x00; referenceOffset = i + 1; }; break;
                    case 'b': codepoint = 0x08; referenceOffset = i + 1; break;
                    case 't': codepoint = 0x09; referenceOffset = i + 1; break;
                    case 'n': codepoint = 0x0A; referenceOffset = i + 1; break;
                    case 'f': codepoint = 0x0C; referenceOffset = i + 1; break;
                    case 'r': codepoint = 0x0D; referenceOffset = i + 1; break;
                    case '"': codepoint = 0x22; referenceOffset = i + 1; break;
                    case '\'': codepoint = 0x27; referenceOffset = i + 1; break;
                    case '\\': codepoint = 0x5C; referenceOffset = i + 1; break;
                }

                if (codepoint == -1) {

                    if (c1 >= '0' && c1 <= '7') {
                        
                        int f = i + 2;
                        while (f < (i + 4) && f < max) { 
                            final char cf = unicodeEscapedText[f];
                            if (!(cf >= '0' && cf <= '7')) {
                                break;
                            }
                            f++;
                        }

                        codepoint = parseIntFromReference(unicodeEscapedText, i + 1, f, 8);

                        if (codepoint > 0xFF) {
                            codepoint = parseIntFromReference(unicodeEscapedText, i + 1, f - 1, 8);
                            referenceOffset = f - 2;
                        } else {
                            referenceOffset = f - 1;
                        }
                    } else {
                        i++;
                        continue;

                    }

                }

            }

            if (i - readOffset > 0) {
                writer.write(unicodeEscapedText, readOffset, (i - readOffset));
            }

            i = referenceOffset;
            readOffset = i + 1;

            if (codepoint > '\uFFFF') {
                writer.write(Character.toChars(codepoint));
            } else {
                writer.write((char)codepoint);
            }

        }

        if (max - readOffset > 0) {
            writer.write(unicodeEscapedText, readOffset, (max - readOffset));
        }


    }




    /**
    * Performs the codePointAt operation
    * @param c1 - the c1
    * @param c2 - the c2
    * @return the codePointAt result
    * */
    private static int codePointAt(final char c1, final char c2) {
        if (Character.isHighSurrogate(c1)) {
            if (c2 >= 0) {
                if (Character.isLowSurrogate(c2)) {
                    return Character.toCodePoint(c1, c2);
                }
            }
        }
        return c1;
    }



}
