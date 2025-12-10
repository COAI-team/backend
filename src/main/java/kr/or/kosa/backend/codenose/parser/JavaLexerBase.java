package kr.or.kosa.backend.codenose.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

/**
 * JavaLexerBase
 * 
 * 역할:
 * ANTLR4가 생성하는 JavaLexer의 기반 클래스입니다.
 * 렉싱 과정에서 필요한 보조 메서드나 멤버 변수를 정의할 수 있습니다.
 */
public abstract class JavaLexerBase extends Lexer {
    public JavaLexerBase(CharStream input) {
        super(input);
    }
}
