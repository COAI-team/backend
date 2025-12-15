package kr.or.kosa.backend.tutor.service;

import kr.or.kosa.backend.tutor.dto.TutorClientMessage;
import kr.or.kosa.backend.tutor.dto.TutorServerMessage;

public interface TutorService {

    TutorServerMessage handleMessage(TutorClientMessage clientMessage);
}
