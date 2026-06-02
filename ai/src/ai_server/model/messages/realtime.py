from pydantic import BaseModel

from ai_server.model._config import camel_config


# AI -> RealTime 직접 발행되는 분석 진행 상황. RealTime 이 user 채널 SSE 로 fan-out 한다.
# 휘발성(진행 표시용)이라 Core/DB 를 거치지 않는다. 종료 상태(ANALYZED/FAILED)는
# 기존대로 Core 콜백 -> REPO_STATE/DOC_STATE 로 전달된다.
class AnalysisProgressData(BaseModel):
    model_config = camel_config()

    target_type: str  # "REPOSITORY" | "RESUME"
    target_id: int
    phase: str  # "EXTRACTING" | "SUMMARIZING" | "EMBEDDING"
    message: str


# RealTime Envelope.payload 형태 ({eventType, data}). Core 의 RealtimeNotifyPayload 와 동일.
class RealtimeNotifyPayload(BaseModel):
    model_config = camel_config()

    event_type: str
    data: AnalysisProgressData
