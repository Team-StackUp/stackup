from ai_server.chain.sentence_split import next_sentences


def test_extracts_completed_sentences_only():
    sents, consumed = next_sentences("첫 문장이다. 둘째 문장", 0)
    assert sents == ["첫 문장이다."]
    assert consumed == len("첫 문장이다.")


def test_no_complete_sentence_returns_empty():
    sents, consumed = next_sentences("아직 끝나지 않은", 0)
    assert sents == []
    assert consumed == 0


def test_multiple_enders_and_newline():
    text = "하나? 둘! 셋\n넷"
    sents, consumed = next_sentences(text, 0)
    assert sents == ["하나?", "둘!", "셋"]
    assert text[consumed:] == "넷"


def test_resume_from_consumed():
    text = "가. 나. 다"
    s1, c1 = next_sentences(text, 0)
    assert s1 == ["가."]
    s2, c2 = next_sentences(text, c1)
    assert s2 == ["나."]
