/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.heliosapm.actors;

import co.paralleluniverse.fibers.Suspendable;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Title: IDDynamicProxy</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.actors.IDDynamicProxy</code></p>
 */

final class IDDynamicProxy {
    /** The method handles lookup */
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    /** The method handles keyed by the method */
    private final Map<Method, ConstantCallSite> callSites = new HashMap<>();

    /**
     * Creates a new IDDynamicProxy
     */
    IDDynamicProxy(Class<?> clazz, Class<?> iface) {
        try {
            /* The target class */
            for (final Method m : iface.getDeclaredMethods()) {
                final MethodHandle methodHandle = LOOKUP.unreflect(m);
                final ConstantCallSite ccs = new ConstantCallSite(methodHandle);
                callSites.put(m, ccs);
            }

            for (final Method m : clazz.getDeclaredMethods()) {
                if (callSites.containsKey(m)) continue;
                final MethodHandle methodHandle = LOOKUP.unreflect(m);
                final ConstantCallSite ccs = new ConstantCallSite(methodHandle);
                callSites.put(m, ccs);
            }
            for (final Method m : clazz.getMethods()) {
                if (callSites.containsKey(m)) continue;
                final MethodHandle methodHandle = LOOKUP.unreflect(m);
                final ConstantCallSite ccs = new ConstantCallSite(methodHandle);
                callSites.put(m, ccs);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Suspendable
    final Object invoke(final Object target, final Method method, final Object... args) throws Throwable {
        final ConstantCallSite ccs = callSites.get(method);
        return ccs.dynamicInvoker().bindTo(target).invoke((BigDecimal) args[0]);
    }
}
