package com.lld.patterns.behavioral.interpreter;

import java.util.Map;

/*
 * INTERPRETER — define a grammar for a simple language and an evaluator for its sentences. Each
 * grammar rule becomes a class; expressions compose into a tree that you evaluate recursively.
 *
 * Rare in interviews, but exactly right for a specific family of questions:
 *   - Rule engines / feature-flag conditions, search-query filters, formula evaluators
 *     ("price > 100 AND category == books"), calculators, regex-lite matchers.
 *
 * Warning: for a real language, use a parser generator. Interpreter suits small, stable grammars.
 */

/** Abstract expression: everything evaluates against a context of variable bindings. */
interface Expression {
    int interpret(Map<String, Integer> context);
}

/** Terminal expression: a literal number. */
class NumberExpression implements Expression {
    private final int value;
    NumberExpression(int value) { this.value = value; }
    public int interpret(Map<String, Integer> ctx) { return value; }
}

/** Terminal expression: a variable looked up in the context. */
class VariableExpression implements Expression {
    private final String name;
    VariableExpression(String name) { this.name = name; }
    public int interpret(Map<String, Integer> ctx) {
        Integer v = ctx.get(name);
        if (v == null) throw new IllegalArgumentException("Unbound variable: " + name);
        return v;
    }
}

/** Non-terminal expressions: compose other expressions. */
class AddExpression implements Expression {
    private final Expression left, right;
    AddExpression(Expression left, Expression right) { this.left = left; this.right = right; }
    public int interpret(Map<String, Integer> ctx) { return left.interpret(ctx) + right.interpret(ctx); }
}

class MultiplyExpression implements Expression {
    private final Expression left, right;
    MultiplyExpression(Expression left, Expression right) { this.left = left; this.right = right; }
    public int interpret(Map<String, Integer> ctx) { return left.interpret(ctx) * right.interpret(ctx); }
}

public class InterpreterDemo {
    public static void main(String[] args) {
        // Build the tree for:  (qty * price) + shipping
        Expression expr = new AddExpression(
                new MultiplyExpression(new VariableExpression("qty"), new VariableExpression("price")),
                new VariableExpression("shipping"));

        Map<String, Integer> ctx = Map.of("qty", 3, "price", 250, "shipping", 40);
        System.out.println("(qty * price) + shipping = " + expr.interpret(ctx));

        // Same tree, different context — the grammar is reusable.
        System.out.println("with qty=10           = "
                + expr.interpret(Map.of("qty", 10, "price", 250, "shipping", 0)));

        // Literals compose the same way.
        Expression twoPlusThree = new AddExpression(new NumberExpression(2), new NumberExpression(3));
        System.out.println("2 + 3                 = " + twoPlusThree.interpret(Map.of()));
    }
}
