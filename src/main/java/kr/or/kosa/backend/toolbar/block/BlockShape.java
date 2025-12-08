package kr.or.kosa.backend.toolbar.block;

// 에디터 블록의 공통 형태 정의
// Freeboard / Codeboard 공통 사용
public interface BlockShape {

    // 프론트에서 생성한 블록 고유 ID
    String getId();

    // 블록 타입 (tiptap, code 등)
    String getType();

    // 블록 콘텐츠 (전송/저장 형식 기준)
    // tiptap: JSON 구조 (Node 트리를 직렬화한 객체)
    // code: 코드 내용을 담는 문자열
    Object getContent();

    // code 블록일 경우 언어 (java, js 등)
    String getLanguage();

    // 블록 출력 순서
    Integer getOrder();
}
