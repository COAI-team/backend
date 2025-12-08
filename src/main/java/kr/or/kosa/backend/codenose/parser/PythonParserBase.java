package kr.or.kosa.backend.codenose.parser;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class PythonParserBase extends Parser {
    public PythonParserBase(TokenStream input) {
        super(input);
    }
}
