package sam4c.light.loader;

import sam4c.light.model.AttributeType;
import sam4c.light.model.NamedContext;
import sam4c.light.model.SecurityModel;
import sam4c.light.model.ref.*;
import sam4c.light.model.rule.SecurityRule;
import sam4c.light.registry.PropertyRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DslParser {

    // -------------------------------------------------------------------------
    // Token types
    // -------------------------------------------------------------------------

    private enum TType {
        HASH_ATTRIBUTE, HASH_CONTEXT, HASH_PROPERTY,
        IDENT, STRING,
        LPAREN, RPAREN, LBRACE, RBRACE,
        EQ, COMMA, COLON, SEMICOL, ARROW,
        EOF
    }

    private record Tok(TType type, String value, int line) {}

    // -------------------------------------------------------------------------
    // Tokenizer
    // -------------------------------------------------------------------------

    private static List<Tok> tokenize(String src) {
        List<Tok> tokens = new ArrayList<>();
        int i = 0;
        int line = 1;
        int len = src.length();

        while (i < len) {
            char c = src.charAt(i);

            if (c == '\n') { line++; i++; continue; }
            if (Character.isWhitespace(c)) { i++; continue; }

            if (c == '/' && i + 1 < len && src.charAt(i + 1) == '/') {
                while (i < len && src.charAt(i) != '\n') i++;
                continue;
            }

            if (c == '#') {
                int start = i;
                i++;
                while (i < len && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
                String word = src.substring(start, i);
                switch (word) {
                    case "#attribute" -> tokens.add(new Tok(TType.HASH_ATTRIBUTE, word, line));
                    case "#context"   -> tokens.add(new Tok(TType.HASH_CONTEXT,   word, line));
                    case "#property"  -> tokens.add(new Tok(TType.HASH_PROPERTY,  word, line));
                    default -> throw new ParseException("Unknown keyword: " + word, line);
                }
                continue;
            }

            if (c == '-' && i + 1 < len && src.charAt(i + 1) == '>') {
                tokens.add(new Tok(TType.ARROW, "->", line));
                i += 2;
                continue;
            }

            switch (c) {
                case '(' -> { tokens.add(new Tok(TType.LPAREN, "(", line)); i++; }
                case ')' -> { tokens.add(new Tok(TType.RPAREN, ")", line)); i++; }
                case '{' -> { tokens.add(new Tok(TType.LBRACE, "{", line)); i++; }
                case '}' -> { tokens.add(new Tok(TType.RBRACE, "}", line)); i++; }
                case '=' -> { tokens.add(new Tok(TType.EQ,     "=", line)); i++; }
                case ',' -> { tokens.add(new Tok(TType.COMMA,  ",", line)); i++; }
                case ':' -> { tokens.add(new Tok(TType.COLON,  ":", line)); i++; }
                case ';' -> { tokens.add(new Tok(TType.SEMICOL,";", line)); i++; }
                default -> {
                    if (c == '"') {
                        i++;
                        int start = i;
                        while (i < len && src.charAt(i) != '"') {
                            if (src.charAt(i) == '\n') line++;
                            i++;
                        }
                        tokens.add(new Tok(TType.STRING, src.substring(start, i), line));
                        i++;
                    } else if (Character.isLetterOrDigit(c) || c == '_') {
                        int start = i;
                        while (i < len && (Character.isLetterOrDigit(src.charAt(i))
                                || src.charAt(i) == '_' || src.charAt(i) == '.')) i++;
                        tokens.add(new Tok(TType.IDENT, src.substring(start, i), line));
                    } else {
                        throw new ParseException("Unexpected character: '" + c + "'", line);
                    }
                }
            }
        }

        tokens.add(new Tok(TType.EOF, "", line));
        return tokens;
    }

    // -------------------------------------------------------------------------
    // Parser state
    // -------------------------------------------------------------------------

    private final List<Tok> tokens;
    private int pos = 0;
    private final PropertyRegistry registry;

    private DslParser(List<Tok> tokens, PropertyRegistry registry) {
        this.tokens = tokens;
        this.registry = registry;
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static SecurityModel parse(File file, PropertyRegistry registry) throws IOException {
        String src = Files.readString(file.toPath());
        return parse(src, registry);
    }

    public static SecurityModel parse(String src, PropertyRegistry registry) {
        List<Tok> tokens = tokenize(src);
        return new DslParser(tokens, registry).parseProgram();
    }

    // -------------------------------------------------------------------------
    // Grammar rules
    // -------------------------------------------------------------------------

    private SecurityModel parseProgram() {
        List<AttributeType> attributes = new ArrayList<>();
        List<NamedContext>  contexts   = new ArrayList<>();
        List<SecurityRule>  rules      = new ArrayList<>();

        while (peek().type() != TType.EOF) {
            switch (peek().type()) {
                case HASH_ATTRIBUTE -> attributes.add(parseAttribute());
                case HASH_CONTEXT   -> contexts.add(parseContext());
                case HASH_PROPERTY  -> rules.add(parseRule());
                default -> throw new ParseException(
                        "Expected #attribute, #context or #property", peek().line());
            }
        }

        return new SecurityModel(attributes, contexts, rules);
    }

    private AttributeType parseAttribute() {
        consume(TType.HASH_ATTRIBUTE);
        String name = expectIdent();
        List<String> values = new ArrayList<>();
        if (peek().type() == TType.EQ) {
            consume(TType.EQ);
            consume(TType.LPAREN);
            values.add(expectIdent());
            while (peek().type() == TType.COMMA) {
                consume(TType.COMMA);
                values.add(expectIdent());
            }
            consume(TType.RPAREN);
        }
        consume(TType.SEMICOL);
        return new AttributeType(name, values);
    }

    private NamedContext parseContext() {
        consume(TType.HASH_CONTEXT);
        String name = expectIdent();
        consume(TType.EQ);
        List<Ref> conditions = new ArrayList<>();
        conditions.add(parseBaseRef());
        while (peek().type() == TType.COLON) {
            consume(TType.COLON);
            conditions.add(parseBaseRef());
        }
        consume(TType.SEMICOL);
        return new NamedContext(name, conditions);
    }

    private SecurityRule parseRule() {
        consume(TType.HASH_PROPERTY);
        String keyword = expectIdent();
        consume(TType.LPAREN);
        List<Ref> args = new ArrayList<>();
        args.add(parseTopRef());
        while (peek().type() == TType.COMMA) {
            consume(TType.COMMA);
            args.add(parseTopRef());
        }
        consume(TType.RPAREN);
        Ref returnRef = null;
        if (peek().type() == TType.ARROW) {
            consume(TType.ARROW);
            returnRef = parseTopRef();
        }
        consume(TType.SEMICOL);
        return registry.create(keyword, args, returnRef);
    }

    // -------------------------------------------------------------------------
    // Reference parsing
    // -------------------------------------------------------------------------

    private Ref parseTopRef() {
        if (peek().type() == TType.LBRACE) {
            return parseGroupRef();
        }
        Ref first = parseBaseRef();
        if (peek().type() == TType.COLON) {
            List<Ref> conditions = new ArrayList<>();
            conditions.add(first);
            while (peek().type() == TType.COLON) {
                consume(TType.COLON);
                conditions.add(parseBaseRef());
            }
            return new ComposedRef(conditions);
        }
        return first;
    }

    private Ref parseGroupRef() {
        consume(TType.LBRACE);
        List<Ref> groups = new ArrayList<>();
        groups.add(parseInlineGroup());
        while (peek().type() == TType.COMMA) {
            consume(TType.COMMA);
            groups.add(parseInlineGroup());
        }
        consume(TType.RBRACE);
        return new ComposedRef(groups);
    }

    private Ref parseInlineGroup() {
        Ref first = parseBaseRef();
        if (peek().type() == TType.COLON) {
            List<Ref> conditions = new ArrayList<>();
            conditions.add(first);
            while (peek().type() == TType.COLON) {
                consume(TType.COLON);
                conditions.add(parseBaseRef());
            }
            return new ComposedRef(conditions);
        }
        return first;
    }

    private Ref parseBaseRef() {
        if (peek().type() == TType.LPAREN) {
            consume(TType.LPAREN);
            String attr = expectIdent();
            consume(TType.EQ);
            String value = peek().type() == TType.STRING
                    ? consume(TType.STRING).value()
                    : expectIdent();
            consume(TType.RPAREN);
            return new ValuedAttrRef(attr, value);
        }
        return new NamedRef(expectIdent());
    }

    // -------------------------------------------------------------------------
    // Token helpers
    // -------------------------------------------------------------------------

    private Tok peek() { return tokens.get(pos); }

    private Tok consume(TType expected) {
        Tok t = tokens.get(pos);
        if (t.type() != expected)
            throw new ParseException(
                    "Expected " + expected + " but got '" + t.value() + "'", t.line());
        pos++;
        return t;
    }

    private Tok consume(TType t1, TType t2) {
        Tok t = tokens.get(pos);
        if (t.type() != t1 && t.type() != t2)
            throw new ParseException(
                    "Expected " + t1 + " or " + t2 + " but got '" + t.value() + "'", t.line());
        pos++;
        return t;
    }

    private String expectIdent() {
        Tok t = tokens.get(pos);
        if (t.type() != TType.IDENT)
            throw new ParseException("Expected identifier but got '" + t.value() + "'", t.line());
        pos++;
        return t.value();
    }
}
