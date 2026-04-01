package com.craftinginterpreters.lox;

import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    private Expr expression() {
        return comma();
    }

    private Expr comma() {
        if (match(COMMA)) {
            commaError();
        }

        Expr expr = equality();

        while (match(COMMA)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private void commaError() {
        Token operator = previous();
        equality();

        throw error(operator, "Expect equality before ','.");
    }

    private Expr equality() {
        if (match(BANG_EQUAL, EQUAL_EQUAL)) {
            equalityError();
        }

        Expr expr = ternary();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = ternary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private void equalityError() {
        Token operator = previous();
        ternary();

        throw error(operator, String.format("\"Expect ternary before '%s'.\"", operator.lexeme));
    }

    private Expr ternary() {
        Expr expr = comparison();

        if (match(QUESTION_MARK)) {
            Token leftOperator = previous();
            Expr middle = expression();
            Token rightOperator = consume(COLON, "Expect ':' after expression.");
            Expr right = ternary();
            expr = new Expr.Ternary(expr, leftOperator, middle, rightOperator, right);
        }

        return expr;
    }

    private Expr comparison() {
        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            commaError();
        }

        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private void comparisonError() {
        Token operator = previous();
        term();

        throw error(operator, String.format("\"Expect term before '%s'.\"", operator.lexeme));
    }

    private Expr term() {
        if (match(PLUS)) {
            termError();
        }

        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private void termError() {
        Token operator = previous();
        factor();

        throw error(operator, String.format("\"Expect factor before '%s'.\"", operator.lexeme));
    }

    private Expr factor() {
        if (match(SLASH, STAR)) {
            factorError();
        }

        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private void factorError() {
        Token operator = previous();
        unary();

        throw error(operator, String.format("\"Expect unary before '%s'.\"", operator.lexeme));
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}
