package com.lld.behavioral.chainofresponsibility;

/**
 * CHAIN OF RESPONSIBILITY — pass a request along a chain of handlers. Each handler decides to
 * process it and/or forward it to the next. Decouples sender from receiver.
 *
 * When to use in LLD:
 *   - Middleware pipelines (auth -> rate-limit -> logging), approval workflows (manager ->
 *     director -> VP by amount), event/exception handling, log-level filtering.
 *
 * Here: an expense-approval chain where each approver handles up to a limit.
 */

abstract class Approver {
    protected Approver next;                       // the next handler in the chain

    Approver linkWith(Approver next) { this.next = next; return next; } // fluent chaining

    void handle(double amount) {
        if (canApprove(amount)) {
            approve(amount);
        } else if (next != null) {
            next.handle(amount);                   // forward to the next handler
        } else {
            System.out.println("No one can approve $" + amount);
        }
    }

    protected abstract boolean canApprove(double amount);
    protected abstract void approve(double amount);
}

class TeamLead extends Approver {
    protected boolean canApprove(double amount) { return amount <= 1_000; }
    protected void approve(double amount) { System.out.println("TeamLead approved $" + amount); }
}

class Manager extends Approver {
    protected boolean canApprove(double amount) { return amount <= 10_000; }
    protected void approve(double amount) { System.out.println("Manager approved $" + amount); }
}

class Director extends Approver {
    protected boolean canApprove(double amount) { return amount <= 100_000; }
    protected void approve(double amount) { System.out.println("Director approved $" + amount); }
}

public class ChainDemo {
    public static void main(String[] args) {
        Approver lead = new TeamLead();
        lead.linkWith(new Manager()).linkWith(new Director()); // build: lead -> manager -> director

        lead.handle(500);      // TeamLead
        lead.handle(7_500);    // Manager
        lead.handle(80_000);   // Director
        lead.handle(500_000);  // nobody
    }
}
