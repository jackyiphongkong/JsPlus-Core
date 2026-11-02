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
package com.killcoding.jsplus;

/**
* Named permissive {@link TextScriptConsole} pool instance: the pool is built
* with the GraalVM host (Java) access policy open ({@code allowAllAccess=true})
* - the default security profile, same as the shared default pool.
* <p>
* This is a lightweight named instance (not a subclass): it holds the one
* shared {@code TextScriptConsole} whose policy is fixed at construction time.
* Select it at the call site via the boolean strategy constructor:
* {@code new TextScript(template, true)} (equivalently
* {@code AllowAllAccessPool.getInstance()}). External config
* ({@code jsplus.textscript.allowallaccess}) never overrides it.
*/
public final class AllowAllAccessPool {

	private static final TextScriptConsole INSTANCE = new TextScriptConsole(true);

	private AllowAllAccessPool() {
	}

	/** Returns the shared permissive-policy pool instance. */
	public static TextScriptConsole getInstance() {
		return INSTANCE;
	}

}
