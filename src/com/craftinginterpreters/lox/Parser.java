package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    // represents the depth of loop.
    private int loopDepth = 0;

    // used by REPL.
    private final boolean allowExpr;
    private Expr lastExpr;

    Expr takeLastExpr() {
        Expr tmp = this.lastExpr;
        this.lastExpr = null;
        return tmp;
    }

    Parser(List<Token> tokens, boolean allowExpr) {
        this.tokens = tokens;
        this.allowExpr = allowExpr;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return statements;
    }

    private Expr expression() {
        return comma();
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(BREAK)) return breakStatement();

        return expressionStatement();
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");
        Stmt initializer;

        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = loopBody();
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt loopBody() {
        loopDepth++;
        Stmt body = statement();
        loopDepth--;
        return body;
    }

    private Stmt breakStatement() {
        if (loopDepth == 0) {
            throw error(previous(), "Break shouldn't appear outside of any enclosing loop.");
        }
        consume(SEMICOLON, "Expect ';' after 'break'.");
        return new Stmt.Break(previous());
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = loopBody();

        return new Stmt.While(condition, body);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();

        if (!allowExpr) {
            consume(SEMICOLON, "Expect ';' after expression.");
            return new Stmt.Expression(expr);
        } else {
            if (check(SEMICOLON)) {
                advance();
                return new Stmt.Expression(expr);
            }

            if (!isAtEnd()) {
                throw error(peek(), "Expect 'EOF' after last expression in REPL.");
            }
            lastExpr = expr;

            return null;
        }
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr comma() {
        if (match(COMMA)) {
            commaError();
        }

        Expr expr = assignment();

        while (match(COMMA)) {
            Token operator = previous();
            Expr right = assignment();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private void commaError() {
        Token operator = previous();
        equality();

        throw error(operator, "Expect equality before ','.");
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
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
            comparisonError();
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

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
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
