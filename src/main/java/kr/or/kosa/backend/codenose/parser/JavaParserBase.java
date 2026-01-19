package kr.or.kosa.backend.codenose.parser;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class JavaParserBase extends Parser {
    public JavaParserBase(TokenStream input) {
        super(input);
    }
}
