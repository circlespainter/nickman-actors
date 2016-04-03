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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;

/**
 * <p>Title: PosAcctImpl</p>
 * <p>Description: The actor impl.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.actors.PosAcctImpl</code></p>
 */

public final class PosAcctImpl implements PosAcct {
    private String name;
    private BigDecimal balance;
    private Date createDate;
    private Date updateDate;

    static Iterator<PosAcct> load() throws InterruptedException, SuspendExecution {
        log("Load all");
        Strand.sleep(10);
        final List<PosAcct> ret = new ArrayList<>();
        for(int i = 0 ; i < 10 ; i++)
            ret.add(new PosAcctImpl("PosAcct-" + ThreadLocalRandom.current().nextLong(), new BigDecimal(ThreadLocalRandom.current().nextLong())));
        return ret.iterator();
    }

    static void log(Object msg) {
        System.err.println(msg);
    }

    /**
     * Creates a new PosAcctImpl
     * @param name The name
     * @param balance The starting balance
     */
    private PosAcctImpl(final String name, final BigDecimal balance) {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("The passed name was null or empty");
        if (balance == null) throw new IllegalArgumentException("The passed balance was null");

        this.name = name.trim();
        this.balance = balance;
        this.createDate = new Date();
    }

    /**
     * {@inheritDoc}
     * @see com.heliosapm.actors.PosAcct#deposit(java.math.BigDecimal)
     */
    @Override
    public final void deposit(BigDecimal amt) throws InterruptedException, SuspendExecution {
        log("Deposit " + amt);
        balance = balance.add(amt);
        updateDate = new Date(System.currentTimeMillis());
        Strand.sleep(10);
    }

    /**
     * {@inheritDoc}
     * @see com.heliosapm.actors.PosAcct#withdraw(java.math.BigDecimal)
     */
    @Override
    public final void withdraw(BigDecimal amt) throws InterruptedException, SuspendExecution {
        log("Withdraw " + amt);
        balance = balance.subtract(amt);
        Strand.sleep(10);
    }

    /**
     * {@inheritDoc}
     * @see java.lang.Object#toString()
     */
    @Override
    public final String toString() {
        return "PosAcct: [" + name + "]";
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final BigDecimal getBalance() {
        return balance;
    }

    @Override
    public final Date getCreateDate() {
        return createDate;
    }

    @Override
    public final Date getUpdateDate() {
        return updateDate;
    }
}
