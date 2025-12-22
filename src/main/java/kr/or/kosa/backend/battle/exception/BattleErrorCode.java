package kr.or.kosa.backend.battle.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum BattleErrorCode implements ErrorCode {
    ROOM_NOT_FOUND("B001", "존재하지 않는 방입니다."),
    ROOM_FULL("B002", "정원이 가득 찼습니다."),
    ALREADY_IN_ROOM("B003", "이미 해당 방에 참여 중입니다."),
    NOT_PARTICIPANT("B004", "참여 중인 사용자만 요청할 수 있습니다."),
    INVALID_STATUS("B005", "현재 상태에서 처리할 수 없습니다."),
    BET_TOO_SMALL("B006", "베팅 금액은 0 이상이어야 합니다."),
    POINT_NOT_ENOUGH("B007", "포인트가 부족합니다."),
    HOLD_ALREADY_EXISTS("B008", "이미 베팅 금액이 보류되었습니다."),
    SETTLEMENT_ALREADY_DONE("B009", "이미 정산이 완료되었습니다."),
    SUBMIT_COOLDOWN("B010", "제출 쿨다운이 만료될 때까지 기다려 주세요."),
    NOT_RUNNING("B011", "진행중에만 제출할 수 있습니다."),
    JUDGE_UNAVAILABLE("B012", "채점 서비스를 사용할 수 없습니다."),
    COUNTDOWN_ALREADY_STARTED("B013", "카운트다운이 이미 시작되었습니다."),
    READY_NOT_ALLOWED("B014", "대기중인 경우에만 Ready를 변경할 수 있습니다."),
    MATCH_ALREADY_FINISHED("B015", "이미 종료된 매치입니다."),
    START_CONDITION_NOT_MET("B016", "두 참가자 모두 준비되어야 시작할 수 있습니다."),
    LOCK_TIMEOUT("B017", "잠금 획득에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_PASSWORD("B018", "잘못된 비밀번호입니다."),
    JOIN_NOT_ALLOWED("B019", "현재 상태에서는 입장할 수 없습니다."),
    ROOM_STATE_INVALID("B020", "방 상태가 올바르지 않습니다."),
    POINT_ACCOUNT_MISSING("B021", "포인트 정보가 없습니다. 다시 로그인 후 시도해 주세요."),
    INSUFFICIENT_POINTS("B022", "포인트가 부족합니다."),
    HOLD_DB_ERROR("B023", "베팅 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    HOLD_UNKNOWN("B024", "베팅 처리 중 시스템 오류가 발생했습니다."),
    KICKED("B025", "해당 방에서 강퇴되었습니다."),
    KICKED_REJOIN_BLOCKED("B026", "강퇴된 방에는 재입장할 수 없습니다."),
    POSTGAME_LOCK("B027", "아직 게임이 종료되지 않았습니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final String message;

    BattleErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
