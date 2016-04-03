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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.MailboxConfig;
import co.paralleluniverse.actors.behaviors.ProxyServerActor;
import co.paralleluniverse.fibers.SuspendExecution;

/**
 * <p>Title: IDProxyServerActor</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.actors.IDProxyServerActor</code></p>
 */

class IDProxyServerActor extends ProxyServerActor {
    private final IDDynamicProxy dynamicProxy;
    private final Object target;

    private static final Logger LOG = LoggerFactory.getLogger(IDProxyServerActor.class);

    /**
     * Creates a new IDProxyServerActor
     */
    IDProxyServerActor(String name, MailboxConfig mailboxConfig, boolean callOnVoidMethods, Object target, Class<?>... interfaces) {
        super(name, mailboxConfig, callOnVoidMethods, target, interfaces);
        this.target = target;
        dynamicProxy = new IDDynamicProxy(target.getClass(), interfaces[0]);
    }

    /** @noinspection DuplicateThrows*/
    @Override
    protected final Object handleCall(final ActorRef<?> from, final Object id, final Invocation m) throws Exception, SuspendExecution {
        try {
            final Object res = dynamicProxy.invoke(target, m.getMethod(), toArr(m.getParams()));
            return res == null ? NULL_RETURN_VALUE : res;
        } catch (final Throwable e) {
            LOG.error("Invocation of [{}:{}] failed", target.getClass().getSimpleName(), m.getMethod().getName(), e);
            assert !(e.getCause() instanceof SuspendExecution);
            log().error("handleCall: Invocation " + m + " has thrown an exception.", e.getCause());
            throw rethrow(e.getCause());
        }
    }

    @Override
    protected final void handleCast(ActorRef<?> from, Object id, Invocation m) throws SuspendExecution {
        try {
            dynamicProxy.invoke(target, m.getMethod(), toArr(m.getParams()));
        } catch (final Throwable e) {
            LOG.error("Invocation of [{}:{}] failed", target.getClass().getSimpleName(), m.getMethod().getName(), e);
            assert !(e.getCause() instanceof SuspendExecution);
            log().error("handleCall: Invocation " + m + " has thrown an exception.", e.getCause());
            throw new RuntimeException(e);
        }
    }

    private static final Object[] EMPTY_ARGS = {};

    private static Object[] toArr(final List<Object> args) {
        if (args == null || args.isEmpty()) return EMPTY_ARGS;
        return args.toArray();
    }

    private static RuntimeException rethrow(Throwable t) throws Exception {
        if (t instanceof Exception)
            throw (Exception) t;
        if (t instanceof Error)
            throw (Error) t;
        throw new RuntimeException(t);
    }
}
