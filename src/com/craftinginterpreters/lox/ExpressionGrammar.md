expression     → comma ;

comma          → commaError
               | equality ( "," equality )* ;

commaError     → "," equality ;

equality       → equalityError
               | ternary ( ( "!=" | "==" ) ternary )* ;

equalityError  → ( "!=" | "==" ) ternary ;

ternary        → comparison ("?" expression ":" ternary)? ;

comparison     → comparisonError
               | term ( ( ">" | ">=" | "<" | "<=" ) term )* ;

comparisonError→ ( ">" | ">=" | "<" | "<=" ) term ;

term           → termError
               | factor ( ( "-" | "+" ) factor )* ;

termError      → "+" factor ;

factor         → factorError
               | unary ( ( "/" | "*" ) unary )* ;

factorError    → ( "/" | "*" ) unary ;

unary          → ( "!" | "-" ) unary
               | primary ;

primary        → NUMBER | STRING | "true" | "false" | "nil"
               | "(" expression ")" ;