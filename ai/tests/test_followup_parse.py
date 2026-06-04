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
