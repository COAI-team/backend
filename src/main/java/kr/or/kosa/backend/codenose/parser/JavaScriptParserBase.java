package kr.or.kosa.backend.codenose.parser;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class JavaScriptParserBase extends Parser {
    public JavaScriptParserBase(TokenStream input) {
        super(input);
    }

    protected boolean notOpenBraceAndNotFunction() {
        return true;
    }

    protected boolean notLineTerminator() {
        return true;
    }

    protected boolean n(String t) {
        return false;
    }

    protected boolean lineTerminatorAhead() {
        return false;
    }

    protected boolean closeBrace() {
        return false;
    }
}
