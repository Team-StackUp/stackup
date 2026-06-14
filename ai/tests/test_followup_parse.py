from ai_server.chain.followup_generation_chain import parse_followup_result


def test_parses_intent_question_meta():
    text = (
        "<intent>NORMAL</intent>"
        "<question>그 설계에서 트랜잭션 경계를 어디에 뒀나요?</question>"
        '<meta>{"specificity": 2, "logic": 3, "structure": "PARTIAL_STAR", "correctness": null}</meta>'
    )
    r = parse_followup_result(text)
    assert r.answer_intent == "NORMAL"
    assert r.followup_question == "그 설계에서 트랜잭션 경계를 어디에 뒀나요?"
    assert r.answer_evaluation.specificity == 2
    assert r.answer_evaluation.correctness is None


def test_fallback_when_no_tags():
    r = parse_followup_result("이건 그냥 질문 한 줄")
    assert r.followup_question == "이건 그냥 질문 한 줄"
    assert r.answer_intent == "NORMAL"
    assert r.answer_evaluation is None


def test_extract_question_only_helper():
    from ai_server.chain.followup_generation_chain import extract_question_span

    assert extract_question_span("<intent>NORMAL</intent><question>안녕") == "안녕"
    assert (
        extract_question_span("<intent>NORMAL</intent><question>안녕</question><meta>{}")
        == "안녕"
    )


def test_streaming_keeps_lt_in_body():
    # 스트리밍 중(닫는 태그 전) 질문 본문의 부등호·제네릭 '<' 를 잘라내면 안 된다.
    from ai_server.chain.followup_generation_chain import extract_question_span

    assert (
        extract_question_span("<intent>NORMAL</intent><question>a < b 인지 확인하세요")
        == "a < b 인지 확인하세요"
    )
    assert (
        extract_question_span("<intent>NORMAL</intent><question>제네릭 List<T> 를 설명하세요")
        == "제네릭 List<T> 를 설명하세요"
    )


def test_streaming_strips_partial_closing_or_meta_tag():
    # 청크 경계로 잘려 들어온 닫는/메타 태그 partial 만 제거한다.
    from ai_server.chain.followup_generation_chain import extract_question_span

    assert extract_question_span("<intent>NORMAL</intent><question>질문입니다?</quest") == "질문입니다?"
    assert extract_question_span("<intent>NORMAL</intent><question>끝났나요?<met") == "끝났나요?"


def test_parses_question_with_lt_when_closed():
    # 닫는 태그가 있으면 '<' 포함 질문이 그대로 보존된다(최종 저장값 잘림 방지).
    text = (
        "<intent>NORMAL</intent>"
        "<question>리스트에서 a < b 를 만족하는 쌍을 어떻게 셀까요?</question>"
        "<meta>{}</meta>"
    )
    r = parse_followup_result(text)
    assert r.followup_question == "리스트에서 a < b 를 만족하는 쌍을 어떻게 셀까요?"
