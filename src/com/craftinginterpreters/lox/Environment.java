package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    final Environment enclosing;

    private final Map<String, Pair> values = new HashMap<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            Pair pair = values.get(name.lexeme);
            if (pair.isInitialized) {
                return pair.value;
            }

            throw new RuntimeError(name,
                    "Uninitialized variable '" + name.lexeme + "'.");
         }

        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    void define(String name, Object value) {
        if (value != null) {
            values.put(name, new Pair(value, true));
        } else {
            values.put(name, new Pair(null, false));
        }
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, new Pair(value, true));
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    private static class Pair {
        Object value;
        boolean isInitialized;

        Pair(Object value, boolean isInitialized) {
            this.value = value;
            this.isInitialized = isInitialized;
        }
    }
}
