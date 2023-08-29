package axl;

import ast.Position;

import java.util.ArrayList;
import java.util.HashMap;

import static axl.Lexer.Token.Keyword.*;
import static axl.Lexer.Token.Type.*;

public class Lexer {

    private final String[] lines;
    private final String filename;

    private final char[] content;
    private int offset;

    private int row;
    private int column;
    private Position position;

    private boolean end = false;

    private Token last;

    public Lexer(String value, String filename) {
        this.content = value.toCharArray();
        this.lines = value.split("\n");
        this.filename = filename;
    }

    private char next() {
        if(offset+1 == content.length)
            end = true;
        if(offset+1 != content.length) {
            if(get() == '\n') {
                row++;
                column = 1;
            } else
                column++;

            end = false;
            return content[offset++];
        }
        return get();
    }

    private char get() {
        return content[offset];
    }

    private Token positioning(Token token) {
        token.position = position;
        last = token;
        return token;
    }

    private boolean isNotEnd() {
        return !end;
    }

    private boolean isEnd() {
        return end;
    }

    private Position getPosition() {
        return new Position(row, column, lines[row-1], filename);
    }

    public ArrayList<Token> tokenize() throws LexerException {
        this.offset = 0;
        this.row = 1;
        this.column = 1;
        this.end = false;
        this.last = null;
        this.position = getPosition();

        ArrayList<Token> tokens = new ArrayList<>();

        while (isNotEnd()) { // main process
            label:
            {
                // skip whitespace and other; exit if end
                if (get() == ' ' || get() == '\n' || get() == '\r' || get() == '\t') {
                    skip();
                    if (isEnd())
                        break label;
                }

                this.position = getPosition();

                if ((get() >= '0' && '9' >= get()) || get() == '.' || get() == '-') {
                    tokens.add(positioning(number()));
                    continue;
                }

                if (Character.isDigit(get()) || Character.isLetter(get()) || get() == '_') {
                    tokens.add(positioning(word())); // and keywords
                    continue;
                }

                if (get() == '"') {
                    tokens.add(positioning(string()));
                    break label;
                }

                if (get() == '\'') {
                    tokens.add(positioning(character()));
                    break label;
                }

                if (get() == '/') {
                    if (offset + 1 != content.length)
                        if (content[offset + 1] == '/') {
                            comment_uno();
                            next();
                            break label;
                        } else if (content[offset + 1] == '*') {
                            comment_multi();
                            next();
                            break label;
                        }
                }

                tokens.add(positioning(op()));
                continue;
            }
            next();
        }

        return tokens;
    }

    private void skip() {
        next();
        while (isNotEnd() && (get() == ' ' || get() == '\n' || get() == '\r' || get() == '\t'))
            next();
    }

    private Token number() throws LexerException {
        Token.Type type;
        if (get() == '.')
            type = Token.Type.FLOAT;
        else
            type = Token.Type.INT;

        if (get() == '-')
            if (last != null)
                switch (last.getType()) {
                    case CHAR, INT, LONG, FLOAT, DOUBLE, WORD, STRING, RPAR, RBRACKET -> {
                        return op();
                    }
                }

        StringBuilder builder = new StringBuilder();

        do {
            if(isEnd())
                break;

            if(get() == '.') {
                if (type == Token.Type.DOUBLE)
                    throw new LexerException("В вещественных числах не может быть больше одного символа '.'", position);
                else
                    type = Token.Type.DOUBLE;
            }

            builder.append(next());
        } while ((get() >= '0' && '9' >= get()) || get() == '.');

        String value = builder.toString();

        if(value.equals("-") || value.equals(".")) {
            offset--;
            column--;
            return op();
        }
        if(get() == 'f' || get() == 'F')
            return new Token(Float.parseFloat(value));
        if(get() == 'd' || get() == 'D')
            return new Token(Double.parseDouble(value));
        if(get() == 'l' || get() == 'L')
            if (type == Token.Type.DOUBLE)
                throw new LexerException("Постфикс 'L' нельзя применить к вещественным числам", position);
            else
                return new Token(Long.parseLong(value));
        if(type == Token.Type.FLOAT)
            return new Token(Float.parseFloat(value));
        return new Token(Integer.parseInt(value));
    }

    private Token word() {
        StringBuilder builder = new StringBuilder();

        do {
            if(isEnd())
                break;

            builder.append(next());
        } while (Character.isDigit(get()) || Character.isLetter(get()) || get() == '_' || (get() >= '0' && '9' >= get()));

        String value = builder.toString();

        if(Token.keywords.containsKey(value))
            return new Token(Token.keywords.get(value));
        return new Token(value, Token.Type.WORD);
    }

    private Token string() throws LexerException {
        StringBuilder builder = new StringBuilder();
        next();
        for(;;) {
            if(isEnd() || get() == '\n')
                throw new LexerException("The string is not closed.", position);

            if(get() == '\\') {
                next();
                builder.append(switch (next()) {
                    case '\\' -> '\\';
                    case '"' -> '"';
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'r' -> '\r';
                    default -> throw new LexerException("Неизвестный символ после '\\' в строке", position);
                });
                continue;
            }

            if (get() == '"')
                break;
            builder.append(next());
        }

        String value = builder.toString();

        return new Token(value, Token.Type.STRING);
    }

    private Token character() throws LexerException {
        next();
        char value;
        if (isEnd() || get() == '\n')
            throw new LexerException("Символ не закрыт", position);
        if (get() == '\'')
            throw new LexerException("Символ не имеет значения", position);
        if (get() == '\\') {
            next();
            value = switch (next()) {
                case '\\' -> '\\';
                case '\'' -> '\'';
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'b' -> '\b';
                case 'r' -> '\r';
                default -> throw new LexerException("Неизвестный символ после '\\'", position);
            };
        } else
            value = next();
        if (get() != '\'')
            throw new LexerException("Символ не закрыт", position);
        return new Token(value);
    }

    private Token op() throws LexerException {
        char first = next();
        char second = isNotEnd() ? get() : 0;
        if (isEnd()) {
            String value = ""+first;
            if(Token.operators.containsKey(value))
                return new Token(Token.operators.get(value));
            else
                throw new LexerException("Неизвестный символ", position);
        } else {
            String value = ""+first+second;
            if(Token.operators.containsKey(value)) {
                next();
                return new Token(Token.operators.get(value));
            } else {
                value = ""+first;
                if(Token.operators.containsKey(value))
                    return new Token(Token.operators.get(value));
                else
                    throw new LexerException("Неизвестный символ", position);
            }
        }
    }

    private void comment_multi() throws LexerException {
        while (isNotEnd())
        {
            if(next() == '*')
                if(get() == '/')
                    break;
        }

        if (isEnd())
            throw new LexerException("Комментарий не закрыт", position);
    }

    private void comment_uno() {
        while (isNotEnd() && !(get() == '\n' || get() == '\r'))
            next();
    }

    public static final class Token {

        private String value_string;
        private int value_int;
        private char value_char;
        private long value_long;
        private float value_float;
        private double value_double;
        private Keyword keyword;

        private final Type type;

        private Position position;

        public Token(Type type) {
            this.type = type;
        }

        public Token(String value, Type type) {
            this.type = type;
            this.value_string = value;
        }

        public Token(int value) {
            this.type = Type.INT;
            this.value_int = value;
        }

        public Token(long value) {
            this.type = Type.LONG;
            this.value_long = value;
        }

        public Token(float value) {
            this.type = Type.FLOAT;
            this.value_float = value;
        }

        public Token(double value) {
            this.type = Type.DOUBLE;
            this.value_double = value;
        }

        public Token(char value) {
            this.type = Type.CHAR;
            this.value_char = value;
        }

        public Token(Keyword value) {
            this.type = Type.KEYWORD;
            this.keyword = value;
        }

        public String getValueString() {
            return value_string;
        }

        public int getValueInt() {
            return value_int;
        }

        public long getValueLong() {
            return value_long;
        }

        public float getValueFloat() {
            return value_float;
        }

        public double getValueDouble() {
            return value_double;
        }

        public char getValueChar() {
            return value_char;
        }

        public Keyword getKeyword() {
            return keyword;
        }

        public Type getType() {
            return type;
        }

        public Position getPosition() {
            return position;
        }

        public static final HashMap<String, Type> operators = new HashMap<>() {{
            put("=",    EQUAL);
            put("+",    PLUS);
            put("++",   DPLUS);
            put("-",    MINUS);
            put("--",   DMINUS);
            put("*",    STAR);
            put("/",    SLASH);
            put("%",    MODULO);
            put(">",    GREATER);
            put(">=",   GREATER_OR_EQUAL);
            put("<",    LESS);
            put("<=",   LESS_OR_EQUAL);
            put("==",   EQUAL_TO);
            put("!=",   NOT_EQUAL_TO);
            put("&&",   AND);
            put("||",   OR);
            put("!",    NOT);
            put("&",    BITWISE_AND);
            put("|",    BITWISE_OR);
            put("^",    BITWISE_XOR);
            put("~",    BITWISE_NOT);
            put("<<",   LEFT_SHIFT);
            put(">>",   RIGHT_SHIFT);
            put("?",    TERNARY1);
            put(":",    TERNARY2);
            put("(",    LPAR);
            put(")",    RPAR);
            put("{",    LBRACE);
            put("}",    RBRACE);
            put("[",    LBRACKET);
            put("]",    RBRACKET);
            put(".",    DOT);
            put(",",    COMMA);
            put(";",    SEMI);
            put("@",    AT);
        }};

        public static final HashMap<String, Keyword> keywords = new HashMap<>() {{
            put("class",          CLASS);
            put("this",           THIS);
            put("package",        PACKAGE);
            put("import",         IMPORT);
            put("as",             AS);
            put("fn",             FUNCTION);
            put("boolean",        BOOL);
            put("bool",           BOOL);
            put("byte",           BYTE);
            put("i8",             BYTE);
            put("char",           Keyword.CHAR);
            put("short",          SHORT);
            put("i16",            SHORT);
            put("int",            Keyword.INT);
            put("i32",            Keyword.INT);
            put("long",           Keyword.LONG);
            put("i64",            Keyword.LONG);
            put("float",          Keyword.FLOAT);
            put("f32",            Keyword.FLOAT);
            put("double",         Keyword.DOUBLE);
            put("f64",            Keyword.DOUBLE);
            put("void",           VOID);
            put("if",             IF);
            put("else",           ELSE);
            put("switch",         SWITCH);
            put("case",           CASE);
            put("while",          WHILE);
            put("for",            FOR);
            put("goto",           GOTO);
            put("try",            TRY);
            put("catch",          CATCH);
            put("finally",        FINALLY);
            put("throws",         THROWS);
            put("throw",          THROW);
            put("instanceof",     INSTANCEOF);
            put("true",           TRUE);
            put("false",          FALSE);
            put("null",           NULL);
            put("public",         PUBLIC);
            put("private",        PRIVATE);
            put("static",         STATIC);
            put("final",         FINAL);
        }};

        public enum Type {
            EQUAL,                  // =
            PLUS,                   // +
            MINUS,                  // -
            DPLUS,                  // ++
            DMINUS,                 // --
            STAR,                   // *
            SLASH,                  // /
            MODULO,                 // %
            GREATER,                // >
            LESS,                   // <
            GREATER_OR_EQUAL,       // >=
            LESS_OR_EQUAL,          // <=
            EQUAL_TO,               // ==
            NOT_EQUAL_TO,           // !=
            AND,                    // &&
            OR,                     // ||
            NOT,                    // !
            BITWISE_AND,            // &
            BITWISE_OR,             // |
            BITWISE_XOR,            // ^
            BITWISE_NOT,            // ~
            LEFT_SHIFT,             // <<
            RIGHT_SHIFT,            // >>
            TERNARY1,               // ?
            TERNARY2,               // :
            LPAR,                   // (
            RPAR,                   // )
            LBRACE,                 // {
            RBRACE,                 // }
            LBRACKET,               // [
            RBRACKET,               // ]
            DOT,                    // .
            COMMA,                  // ,
            SEMI,                   // ;
            AT,                     // @
            INT,
            LONG,
            FLOAT,
            DOUBLE,
            CHAR,
            STRING,
            KEYWORD,
            WORD
        }

        public enum Keyword {
            CLASS,
            THIS,
            PACKAGE,
            IMPORT,
            AS,
            FUNCTION,
            BOOL,
            BYTE,
            CHAR,
            SHORT,
            INT,
            LONG,
            FLOAT,
            DOUBLE,
            VOID,
            IF,
            ELSE,
            SWITCH,
            CASE,
            WHILE,
            FOR,
            GOTO,
            TRY,
            CATCH,
            FINALLY,
            THROWS,
            THROW,
            INSTANCEOF,
            TRUE,
            FALSE,
            NULL,
            PUBLIC,
            PRIVATE,
            STATIC,
            FINAL
        }

        private static final String format = "%-16s: %-32s : %d:%d";

        public String getMessage() {
            return switch (type) {
                case INT -> format.formatted(type.name(), value_int, position.getRow(), position.getColumn());
                case LONG -> format.formatted(type.name(), value_long, position.getRow(), position.getColumn());
                case FLOAT -> format.formatted(type.name(), value_float, position.getRow(), position.getColumn());
                case DOUBLE -> format.formatted(type.name(), value_double, position.getRow(), position.getColumn());
                case WORD -> format.formatted(type.name(), value_string, position.getRow(), position.getColumn());
                case STRING -> format.formatted(type.name(), value_string.replaceAll("\n", "\\n").replaceAll("\t", "\\t").replaceAll("\b", "\\b").replaceAll("\r", "\\r"), position.getRow(), position.getColumn());
                case KEYWORD -> format.formatted(type.name(), keyword.name(), position.getRow(), position.getColumn());
                default -> format.formatted(type.name(), "", position.getRow(), position.getColumn());
            };
        }

    }

}
