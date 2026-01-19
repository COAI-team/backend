package kr.or.kosa.backend.codenose.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

public abstract class JavaScriptLexerBase extends Lexer {
    public JavaScriptLexerBase(CharStream input) {
        super(input);
    }

    // Predicators and other methods used by JavaScriptLexer.g4
    protected boolean IsStartOfFile() {
        return false;
    }

    protected boolean IsRegexPossible() {
        return false;
    }

    protected void ProcessOpenBrace() {
    }

    protected boolean IsInTemplateString() {
        return false;
    }

    protected void ProcessTemplateCloseBrace() {
    }

    protected void ProcessCloseBrace() {
    }

    protected boolean IsStrictMode() {
        return false;
    }

    protected void ProcessStringLiteral() {
    }

    protected void ProcessTemplateOpenBrace() {
    }
}
