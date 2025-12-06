package kr.or.kosa.backend.codenose.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

public abstract class JavaLexerBase extends Lexer {
    public JavaLexerBase(CharStream input) {
        super(input);
    }
}
